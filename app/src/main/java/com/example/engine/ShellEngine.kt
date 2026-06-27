package com.example.engine

import android.content.Context
import com.example.api.GeminiClient
import com.example.api.GeminiContent
import com.example.api.GeminiPart
import com.example.api.GeminiRequest
import com.example.data.ShellDatabase
import com.example.data.ShellRepository
import com.example.data.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class Line {
    data class Input(val path: String, val text: String) : Line()
    data class Output(val text: String, val isError: Boolean = false) : Line()
}

class ShellEngine(private val context: Context) {
    private val database = ShellDatabase.getDatabase(context)
    private val repository = ShellRepository(database.shellDao())

    private val _terminalLines = MutableStateFlow<List<Line>>(emptyList())
    val terminalLines: StateFlow<List<Line>> = _terminalLines.asStateFlow()

    private val _currentPath = MutableStateFlow("/home")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    // Package manager state: Installed packages
    private val installedPackages = mutableSetOf("coreutils", "pkg", "help", "bash", "netutils")

    // Environment variables
    private val environment = mutableMapOf(
        "USER" to "infinity",
        "SHELL" to "/bin/ishell",
        "HOME" to "/home",
        "PATH" to "/bin:/usr/bin",
        "TERM" to "xterm-256color"
    )

    // Current REPL environment if any
    private var activeRepl: String? = null
    private val replBuffer = mutableListOf<String>()

    init {
        // Initialize terminal lines with interactive welcome information
        addOutput("Welcome to Infinity Shell v2.1.0-Release")
        addOutput("Complete Linux-like environment & package ecosystem.")
        addOutput("Type 'help' to view available commands, 'pkg list' to check packages.")
        addOutput("Try the AI assistant integration by executing 'gemini <your query>'.")
        addOutput("")
    }

    fun addOutput(text: String, isError: Boolean = false) {
        _terminalLines.value = _terminalLines.value + Line.Output(text, isError)
    }

    fun clearScreen() {
        _terminalLines.value = emptyList()
    }

    suspend fun execute(commandLine: String) {
        val trimmed = commandLine.trim()
        _terminalLines.value = _terminalLines.value + Line.Input(_currentPath.value, trimmed)
        if (trimmed.isEmpty()) return

        // Handle Active REPL Environments
        if (activeRepl != null) {
            handleReplInput(trimmed)
            return
        }

        // Add to persistent DB command history
        repository.insertCommandHistory(trimmed)

        val parts = trimmed.split("\\s+".toRegex())
        val command = parts[0]
        val args = parts.drop(1)

        when (command) {
            "help" -> showHelp()
            "clear" -> clearScreen()
            "pwd" -> addOutput(_currentPath.value)
            "ls" -> listDirectory()
            "cd" -> changeDirectory(args.getOrNull(0))
            "mkdir" -> makeDirectory(args.getOrNull(0))
            "touch" -> createFile(args.getOrNull(0))
            "rm" -> removeFileOrDir(args.getOrNull(0))
            "cat" -> viewFileContent(args.getOrNull(0))
            "echo" -> echo(args)
            "date" -> addOutput(SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US).format(Date()))
            "whoami" -> addOutput(environment["USER"] ?: "infinity")
            "env" -> listEnv()
            "export" -> setEnv(args.getOrNull(0))
            "pkg" -> handlePkg(args)
            "python", "python3" -> startRepl("python")
            "js", "node" -> startRepl("javascript")
            "ruby" -> startRepl("ruby")
            "curl" -> handleCurl(args)
            "wget" -> handleWget(args)
            "ping" -> handlePing(args.getOrNull(0))
            "nslookup" -> handleNsLookup(args.getOrNull(0))
            "gemini" -> handleGemini(args.joinToString(" "))
            "history" -> showHistory()
            "neofetch" -> showNeofetch()
            "uname" -> addOutput("InfinityShell Linux-kernel-mock 5.15.0-76-generic arm64")
            "cmatrix" -> showCmatrix()
            else -> addOutput("ishell: command not found: $command. Type 'help' to see list of options.", true)
        }
    }

    private fun showHelp() {
        addOutput("=== Infinity Shell Core Commands ===")
        addOutput("  ls                List files and directories in current folder")
        addOutput("  cd <dir>          Change workspace path (e.g. cd /, cd /home)")
        addOutput("  mkdir <name>      Create a new virtual directory")
        addOutput("  touch <file>      Create a virtual file")
        addOutput("  rm <target>       Delete a virtual file or directory")
        addOutput("  cat <file>        Read virtual file contents")
        addOutput("  echo <text>       Display line text on output")
        addOutput("  pwd               Print active directory path")
        addOutput("  whoami            Show current username")
        addOutput("  env               Print all current environment variables")
        addOutput("  export KEY=VAL    Set or change an environment variable")
        addOutput("  neofetch          Show stylish system overview")
        addOutput("  cmatrix           Sleek cascading matrix visual effect")
        addOutput("")
        addOutput("=== Network Utilities ===")
        addOutput("  curl <url>        Send request and retrieve source payload")
        addOutput("  wget <url>        Download file into virtual drive workspace")
        addOutput("  ping <host>       Mock active system telemetry heartbeat checks")
        addOutput("  nslookup <host>   Perform domain namespace hostname lookups")
        addOutput("")
        addOutput("=== Package Ecosystem ===")
        addOutput("  pkg list          List all workspace environment packages")
        addOutput("  pkg install <pkg> Add developer libraries or compilers")
        addOutput("  pkg uninstall <p> Remove components")
        addOutput("")
        addOutput("=== AI Assistants & REPLs ===")
        addOutput("  gemini <query>    AI terminal agent prompt and synthesis")
        addOutput("  python            Interactive Python sandboxed REPL")
        addOutput("  js                Interactive Node/JS sandboxed REPL")
        addOutput("  ruby              Interactive Ruby sandboxed REPL")
        addOutput("  clear             Clear screen contents")
    }

    private suspend fun listDirectory() {
        val files = repository.getAllVirtualFiles()
        val current = _currentPath.value
        val matches = files.filter {
            val parent = getParentPath(it.path)
            parent == current && it.path != current
        }

        if (matches.isEmpty() && current == "/home") {
            // Seed a default readme if empty
            val readme = VirtualFile("/home/welcome.txt", false, "Welcome to Infinity Shell. Feel free to run complex commands here.")
            repository.insertVirtualFile(readme)
            addOutput("welcome.txt (file - 70B)")
            return
        }

        if (matches.isEmpty()) {
            addOutput("(empty directory)")
            return
        }

        matches.forEach {
            val name = it.path.substringAfterLast("/")
            val typeStr = if (it.isDirectory) "dir" else "file"
            val sizeStr = if (it.isDirectory) "" else " - ${it.content.length}B"
            addOutput("$name ($typeStr$sizeStr)")
        }
    }

    private suspend fun changeDirectory(target: String?) {
        if (target == null || target == "~") {
            _currentPath.value = "/home"
            return
        }
        val resolved = resolvePath(target)
        if (resolved == "/") {
            _currentPath.value = "/"
            return
        }
        val fileObj = repository.getVirtualFile(resolved)
        if (fileObj != null && fileObj.isDirectory) {
            _currentPath.value = resolved
        } else {
            addOutput("ishell: cd: no such directory: $target", true)
        }
    }

    private suspend fun makeDirectory(name: String?) {
        if (name == null) {
            addOutput("ishell: mkdir: missing operand", true)
            return
        }
        val targetPath = resolvePath(name)
        repository.insertVirtualFile(VirtualFile(targetPath, isDirectory = true))
        addOutput("Directory created: $targetPath")
    }

    private suspend fun createFile(name: String?) {
        if (name == null) {
            addOutput("ishell: touch: missing operand", true)
            return
        }
        val targetPath = resolvePath(name)
        repository.insertVirtualFile(VirtualFile(targetPath, isDirectory = false, content = ""))
        addOutput("File created: $targetPath")
    }

    private suspend fun removeFileOrDir(target: String?) {
        if (target == null) {
            addOutput("ishell: rm: missing operand", true)
            return
        }
        val targetPath = resolvePath(target)
        val file = repository.getVirtualFile(targetPath)
        if (file != null) {
            repository.deleteVirtualFileByPath(targetPath)
            addOutput("Removed: $targetPath")
        } else {
            addOutput("ishell: rm: cannot remove '$target': No such file or directory", true)
        }
    }

    private suspend fun viewFileContent(target: String?) {
        if (target == null) {
            addOutput("ishell: cat: missing operand", true)
            return
        }
        val targetPath = resolvePath(target)
        val file = repository.getVirtualFile(targetPath)
        if (file != null) {
            if (file.isDirectory) {
                addOutput("ishell: cat: $target: Is a directory", true)
            } else {
                addOutput(file.content)
            }
        } else {
            addOutput("ishell: cat: $target: No such file or directory", true)
        }
    }

    private fun echo(args: List<String>) {
        val fullText = args.joinToString(" ")
        addOutput(fullText)
    }

    private fun listEnv() {
        environment.forEach { (k, v) ->
            addOutput("$k=$v")
        }
    }

    private fun setEnv(arg: String?) {
        if (arg == null || !arg.contains("=")) {
            addOutput("Usage: export KEY=VALUE", true)
            return
        }
        val parts = arg.split("=", limit = 2)
        val key = parts[0].trim()
        val value = parts[1].trim()
        environment[key] = value
        addOutput("Set environment variable: $key -> $value")
    }

    private fun handlePkg(args: List<String>) {
        val action = args.getOrNull(0)
        val target = args.getOrNull(1)

        when (action) {
            "list", "all" -> {
                addOutput("=== Installed Packages ===")
                installedPackages.forEach { addOutput("  $it (active)") }
                addOutput("")
                addOutput("=== Available Repository Packages ===")
                val available = listOf("clang", "git", "nodejs", "ffmpeg", "neovim", "htop", "nmap", "openssh", "tmux")
                available.forEach {
                    val status = if (installedPackages.contains(it)) "[installed]" else "[available]"
                    addOutput("  $it $status")
                }
            }
            "install" -> {
                if (target == null) {
                    addOutput("Error: package name unspecified. Use 'pkg install <package>'", true)
                    return
                }
                addOutput("Connecting to mirrors.infinityshell.org...")
                addOutput("Fetching package headers for target: '$target'...")
                addOutput("Resolving dependencies... Done.")
                addOutput("Downloading $target-1.0.4-arm64.deb...")
                addOutput("Unpacking archive into local workspace namespace...")
                installedPackages.add(target)
                addOutput("Successfully installed package: $target. Shell modules updated.")
            }
            "uninstall" -> {
                if (target == null) {
                    addOutput("Error: package name unspecified. Use 'pkg uninstall <package>'", true)
                    return
                }
                if (installedPackages.contains(target)) {
                    installedPackages.remove(target)
                    addOutput("Removing modules for target: $target...")
                    addOutput("Package $target uninstalled successfully.")
                } else {
                    addOutput("Error: package '$target' is not installed in workspace.", true)
                }
            }
            else -> {
                addOutput("Usage: pkg [list | install <package> | uninstall <package>]", true)
            }
        }
    }

    private fun startRepl(lang: String) {
        activeRepl = lang
        replBuffer.clear()
        addOutput("=== Interactive $lang REPL Environment ===")
        addOutput("Type code and hit enter to execute. Enter 'exit' or 'quit' to close.")
        addOutput(if (lang == "python") ">>> " else "> ")
    }

    private fun handleReplInput(input: String) {
        if (input == "exit" || input == "quit") {
            addOutput("Exited interactive ${activeRepl?.uppercase()} REPL session.")
            activeRepl = null
            return
        }

        replBuffer.add(input)

        // Generate custom interactive evaluation responses
        when (activeRepl) {
            "python" -> {
                if (input.contains("print")) {
                    val inside = input.substringAfter("print(").substringBeforeLast(")")
                    addOutput(inside.trim { it == '"' || it == '\'' })
                } else if (input.contains("=")) {
                    addOutput("") // Variable assignment
                } else if (input.contains("+") || input.contains("-") || input.contains("*") || input.contains("/")) {
                    val result = evaluateMathExpression(input)
                    addOutput(result)
                } else {
                    addOutput("Evaluated python output: Success (no standard stdout stream)")
                }
                addOutput(">>> ")
            }
            "javascript" -> {
                if (input.contains("console.log")) {
                    val inside = input.substringAfter("console.log(").substringBeforeLast(")")
                    addOutput(inside.trim { it == '"' || it == '\'' })
                } else if (input.contains("+") || input.contains("-") || input.contains("*") || input.contains("/")) {
                    val result = evaluateMathExpression(input)
                    addOutput(result)
                } else {
                    addOutput("JS Expression Evaluated: undefined")
                }
                addOutput("> ")
            }
            "ruby" -> {
                if (input.contains("puts")) {
                    val inside = input.substringAfter("puts ")
                    addOutput(inside.trim { it == '"' || it == '\'' })
                } else {
                    addOutput("=> nil")
                }
                addOutput("irb(main):001:0> ")
            }
        }
    }

    private fun evaluateMathExpression(expr: String): String {
        return try {
            val sanitized = expr.replace("[^0-9+\\-*/().\\s]".toRegex(), "")
            val parts = sanitized.split("(?<=[-+*/])|(?=[-+*/])".toRegex()).map { it.trim() }
            if (parts.size >= 3) {
                val num1 = parts[0].toDouble()
                val op = parts[1]
                val num2 = parts[2].toDouble()
                val res = when (op) {
                    "+" -> num1 + num2
                    "-" -> num1 - num2
                    "*" -> num1 * num2
                    "/" -> num1 / num2
                    else -> 0.0
                }
                res.toString()
            } else {
                "Expression parse failed"
            }
        } catch (e: Exception) {
            "Calculation Error"
        }
    }

    private fun handleCurl(args: List<String>) {
        val url = args.getOrNull(0)
        if (url == null) {
            addOutput("curl: try 'curl --help' or 'curl <url>' for more information", true)
            return
        }
        addOutput("Connecting to host endpoint: $url ...")
        addOutput("HTTP/1.1 200 OK")
        addOutput("Content-Type: text/html; charset=UTF-8")
        addOutput("Server: InfinityShell-Web-Receiver")
        addOutput("")
        addOutput("<!DOCTYPE html><html><head><title>Infinity Shell Mock Portal</title></head>")
        addOutput("<body><h1>Connected Successfully!</h1><p>Client terminal user-agent: InfinityShell/2.1</p></body></html>")
    }

    private suspend fun handleWget(args: List<String>) {
        val url = args.getOrNull(0)
        if (url == null) {
            addOutput("wget: missing URL option", true)
            return
        }
        val filename = url.substringAfterLast("/").ifEmpty { "downloaded_index.html" }
        addOutput("Connecting to remote servers...")
        addOutput("Requesting payload...")
        addOutput("Length: 1048 (1.0K) [text/plain]")
        addOutput("Saving to: '$filename'")
        addOutput("")
        addOutput("100%[======================================>] 1,048       --.-KB/s   in 0.01s")
        addOutput("")

        val resolved = resolvePath(filename)
        repository.insertVirtualFile(
            VirtualFile(
                path = resolved,
                isDirectory = false,
                content = "Downloaded content from URL: $url\nTimestamp: ${Date()}\nSize: 1048 bytes"
            )
        )
        addOutput("wget: saved file successfully to $resolved")
    }

    private fun handlePing(host: String?) {
        if (host == null) {
            addOutput("ping: missing host destination", true)
            return
        }
        addOutput("PING $host (127.0.0.1) 56(84) bytes of data.")
        addOutput("64 bytes from $host (127.0.0.1): icmp_seq=1 ttl=64 time=0.045 ms")
        addOutput("64 bytes from $host (127.0.0.1): icmp_seq=2 ttl=64 time=0.042 ms")
        addOutput("64 bytes from $host (127.0.0.1): icmp_seq=3 ttl=64 time=0.049 ms")
        addOutput("--- $host ping statistics ---")
        addOutput("3 packets transmitted, 3 received, 0% packet loss, time 2012ms")
        addOutput("rtt min/avg/max/mdev = 0.042/0.045/0.049/0.004 ms")
    }

    private fun handleNsLookup(host: String?) {
        if (host == null) {
            addOutput("nslookup: missing host query", true)
            return
        }
        addOutput("Server:         8.8.8.8")
        addOutput("Address:        8.8.8.8#53")
        addOutput("")
        addOutput("Non-authoritative answer:")
        addOutput("Name:   $host")
        addOutput("Address: 142.250.190.46")
    }

    private suspend fun handleGemini(query: String) {
        if (query.isBlank()) {
            addOutput("gemini: empty assistant query. Usage: gemini <your question>", true)
            return
        }
        addOutput("Inquiring Gemini AI Agent core models...")
        val response = callGeminiApi(query)
        addOutput("[Gemini Assistant Response]:")
        response.split("\n").forEach { line ->
            addOutput(line)
        }
    }

    private suspend fun callGeminiApi(prompt: String): String {
        return try {
            val key = com.example.BuildConfig.GEMINI_API_KEY
            if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
                return "Gemini API key is not configured yet. Configure it in the Secrets panel in AI Studio."
            }
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
            )
            val service = GeminiClient.service
            val response = service.generateContent(key, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Received empty response from Gemini service."
        } catch (e: Exception) {
            "Gemini Call Failed: ${e.localizedMessage}. Check internet connectivity or API keys configuration."
        }
    }

    private fun showHistory() {
        addOutput("History log parsing not fully populated - use persistent search view")
    }

    private fun showNeofetch() {
        addOutput("      .---.        infinity@shell")
        addOutput("     /     \\       --------------")
        addOutput("     \\_.._./       OS: InfinityShell Android Client v2.1")
        addOutput("     `----'        Host: Streaming Android Emulator Studio")
        addOutput("    /      \\       Kernel: Android 12 Linux Kernel Mock")
        addOutput("   |  (o)(o) |     Uptime: 23 hours, 45 mins")
        addOutput("   |   \\__/  |     Packages: ${installedPackages.size} (pkg)")
        addOutput("    \\______/      Shell: ishell-zsh core v1.9")
        addOutput("     _||_||_       Resolution: 1080x2400")
        addOutput("    (___(___)      UI Theme: Neon Cyberpunk Material 3")
        addOutput("                   CPU: Arm64 Snapdragon-V8 Virtual Core")
        addOutput("                   Memory: 12GB RAM (8GB Sandbox Allocated)")
    }

    private fun showCmatrix() {
        addOutput("Initializing Cascading CMatrix visual stack:")
        addOutput("1 0 1 1 0 0 1 0 1 1 0 1 0 1 0 1 1 0")
        addOutput("0 1 0 0 1 1 0 1 0 0 1 0 1 0 1 0 0 1")
        addOutput("1 1 0 1 0 1 1 0 1 1 0 1 0 1 1 0 1 0")
        addOutput("0 0 1 0 1 0 0 1 0 0 1 1 0 1 0 1 0 1")
        addOutput("System Matrix visualization finalized. Press custom buttons below to inspect.")
    }

    private fun resolvePath(target: String): String {
        val current = _currentPath.value
        return when {
            target.startsWith("/") -> target
            current == "/" -> "/$target"
            else -> "$current/$target"
        }
    }

    private fun getParentPath(path: String): String {
        if (path == "/" || !path.contains("/")) return "/"
        val index = path.lastIndexOf("/")
        if (index == 0) return "/"
        return path.substring(0, index)
    }
}

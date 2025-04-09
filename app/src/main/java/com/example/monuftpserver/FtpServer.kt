package com.example.monuftpserver

import android.content.Context
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class FtpServer(private val context: Context, private val port: Int, private val statusCallback: (String) -> Unit) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val dataPort = port + 1  // Use next port for data connections
    private val fileSystem = FileSystem(context)
    private var dataServerSocket: ServerSocket? = null
    private var currentType = "A" // ASCII mode by default
    private var renameFrom: String? = null

    fun start() {
        isRunning = true
        thread {
            try {
                serverSocket = ServerSocket(port)
                statusCallback("Running on port $port")
                
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                statusCallback("Error: ${e.message}")
            } finally {
                closeDataConnection()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            closeDataConnection()
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        serverSocket = null
    }

    private fun closeDataConnection() {
        try {
            dataServerSocket?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        dataServerSocket = null
    }

    private fun openDataConnection(): ServerSocket? {
        closeDataConnection() // Close any existing connection
        return try {
            dataServerSocket = ServerSocket(dataPort)
            dataServerSocket
        } catch (e: Exception) {
            null
        }
    }

    private fun handleClient(clientSocket: Socket) {
        thread {
            try {
                val clientAddress = clientSocket.inetAddress.hostAddress
                statusCallback("New connection from $clientAddress")
                
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val writer = PrintWriter(clientSocket.getOutputStream(), true)

                // Send welcome message
                writer.println("220 FTP Server Ready")

                var currentCommand = ""
                var currentArg = ""

                while (isRunning) {
                    val line = reader.readLine() ?: break
                    val parts = line.split(" ", limit = 2)
                    currentCommand = parts[0].uppercase()
                    currentArg = if (parts.size > 1) parts[1] else ""
                    
                    statusCallback("Client $clientAddress: $currentCommand $currentArg")
                    
                    when (currentCommand) {
                        "USER" -> writer.println("331 User name okay, need password")
                        "PASS" -> writer.println("230 User logged in")
                        "SYST" -> writer.println("215 UNIX Type: L8")
                        "FEAT" -> {
                            writer.println("211-Features:")
                            writer.println(" UTF8")
                            writer.println(" SIZE")
                            writer.println("211 End")
                        }
                        "PWD" -> writer.println("257 \"${fileSystem.getCurrentDirectory()}\" is current directory")
                        "CWD" -> {
                            if (fileSystem.changeDirectory(currentArg)) {
                                writer.println("250 Directory changed to ${fileSystem.getCurrentDirectory()}")
                            } else {
                                writer.println("550 Failed to change directory")
                            }
                        }
                        "CDUP" -> {
                            if (fileSystem.changeDirectory("..")) {
                                writer.println("250 Directory changed to ${fileSystem.getCurrentDirectory()}")
                            } else {
                                writer.println("550 Failed to change directory")
                            }
                        }
                        "TYPE" -> {
                            currentType = currentArg.uppercase()
                            writer.println("200 Type set to $currentType")
                        }
                        "PASV" -> {
                            val dataServer = openDataConnection()
                            if (dataServer != null) {
                                val localAddress = clientSocket.localAddress.address
                                val p1 = dataPort / 256
                                val p2 = dataPort % 256
                                writer.println("227 Entering Passive Mode (${localAddress[0]},${localAddress[1]},${localAddress[2]},${localAddress[3]},$p1,$p2)")
                            } else {
                                writer.println("425 Can't open data connection")
                            }
                        }
                        "LIST" -> {
                            writer.println("150 Opening data connection for directory list")
                            try {
                                val dataServer = dataServerSocket ?: openDataConnection()
                                if (dataServer != null) {
                                    val dataSocket = dataServer.accept()
                                    val dataWriter = PrintWriter(dataSocket.getOutputStream(), true)
                                    
                                    // Get actual file listing
                                    val fileList = fileSystem.listFiles()
                                    for (fileEntry in fileList) {
                                        dataWriter.println(fileEntry)
                                    }
                                    
                                    dataSocket.close()
                                    writer.println("226 Transfer complete")
                                } else {
                                    writer.println("425 Can't open data connection")
                                }
                            } catch (e: Exception) {
                                writer.println("425 Can't open data connection: ${e.message}")
                            } finally {
                                closeDataConnection()
                            }
                        }
                        "RETR" -> {
                            val fileName = currentArg
                            val fileSize = fileSystem.getFileSize(fileName)
                            
                            if (fileSize >= 0) {
                                writer.println("150 Opening data connection for $fileName ($fileSize bytes)")
                                try {
                                    val dataServer = dataServerSocket ?: openDataConnection()
                                    if (dataServer != null) {
                                        val dataSocket = dataServer.accept()
                                        val outputStream = dataSocket.getOutputStream()
                                        
                                        fileSystem.getFileInputStream(fileName)?.use { inputStream ->
                                            val buffer = ByteArray(8192)
                                            var bytesRead: Int
                                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                outputStream.write(buffer, 0, bytesRead)
                                            }
                                            outputStream.flush()
                                        }
                                        
                                        dataSocket.close()
                                        writer.println("226 Transfer complete")
                                    } else {
                                        writer.println("425 Can't open data connection")
                                    }
                                } catch (e: Exception) {
                                    writer.println("426 Transfer aborted: ${e.message}")
                                } finally {
                                    closeDataConnection()
                                }
                            } else {
                                writer.println("550 File not found or access denied")
                            }
                        }
                        "STOR" -> {
                            val fileName = currentArg
                            writer.println("150 Opening data connection for $fileName")
                            
                            try {
                                val dataServer = dataServerSocket ?: openDataConnection()
                                if (dataServer != null) {
                                    val dataSocket = dataServer.accept()
                                    val inputStream = dataSocket.getInputStream()
                                    
                                    fileSystem.getFileOutputStream(fileName)?.use { outputStream ->
                                        val buffer = ByteArray(8192)
                                        var bytesRead: Int
                                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                            outputStream.write(buffer, 0, bytesRead)
                                        }
                                        outputStream.flush()
                                    }
                                    
                                    dataSocket.close()
                                    writer.println("226 Transfer complete")
                                } else {
                                    writer.println("425 Can't open data connection")
                                }
                            } catch (e: Exception) {
                                writer.println("426 Transfer aborted: ${e.message}")
                            } finally {
                                closeDataConnection()
                            }
                        }
                        "DELE" -> {
                            if (fileSystem.deleteFile(currentArg)) {
                                writer.println("250 File deleted")
                            } else {
                                writer.println("550 File not found or access denied")
                            }
                        }
                        "MKD" -> {
                            if (fileSystem.makeDirectory(currentArg)) {
                                writer.println("257 Directory created")
                            } else {
                                writer.println("550 Failed to create directory")
                            }
                        }
                        "RMD" -> {
                            if (fileSystem.removeDirectory(currentArg)) {
                                writer.println("250 Directory removed")
                            } else {
                                writer.println("550 Directory not found or not empty")
                            }
                        }
                        "RNFR" -> {
                            renameFrom = currentArg
                            writer.println("350 Ready for RNTO")
                        }
                        "RNTO" -> {
                            val from = renameFrom
                            if (from != null) {
                                if (fileSystem.renameFile(from, currentArg)) {
                                    writer.println("250 File renamed")
                                } else {
                                    writer.println("550 Rename failed")
                                }
                                renameFrom = null
                            } else {
                                writer.println("503 RNFR required first")
                            }
                        }
                        "SIZE" -> {
                            val fileSize = fileSystem.getFileSize(currentArg)
                            if (fileSize >= 0) {
                                writer.println("213 $fileSize")
                            } else {
                                writer.println("550 File not found or access denied")
                            }
                        }
                        "QUIT" -> {
                            writer.println("221 Goodbye")
                            break
                        }
                        else -> writer.println("500 Unknown command: $currentCommand")
                    }
                }
                clientSocket.close()
            } catch (e: Exception) {
                statusCallback("Client error: ${e.message}")
            } finally {
                closeDataConnection()
            }
        }
    }
}

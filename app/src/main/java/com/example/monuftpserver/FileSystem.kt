package com.example.monuftpserver

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles file system operations for the FTP server
 * Provides methods for file manipulation and navigation
 */
class FileSystem(private val context: Context) {
    private var currentDirectory: String
    private val rootDirectory: String
    private val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)
    private val TAG = "FileSystem"
    private var rootDocumentFile: DocumentFile? = null
    private var currentDocumentFile: DocumentFile? = null
    private var useDocumentFile = false
    
    init {
        // Use app-specific storage for Android 10+ (API 29+)
        rootDirectory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(null)?.path ?: ""
        } else {
            Environment.getExternalStorageDirectory().path
        }
        currentDirectory = rootDirectory
        
        // Initialize DocumentFile for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            rootDocumentFile = DocumentFile.fromFile(File(rootDirectory))
            currentDocumentFile = rootDocumentFile
            useDocumentFile = true
        }
        
        Log.d(TAG, "Initialized with root directory: $rootDirectory, useDocumentFile: $useDocumentFile")
    }

    /**
     * Get the current working directory
     */
    fun getCurrentDirectory(): String {
        return currentDirectory
    }

    /**
     * Change to the specified directory
     * @param path The directory to change to
     * @return true if successful, false otherwise
     */
    fun changeDirectory(path: String): Boolean {
        try {
            val newPath = if (path.startsWith("/")) {
                // Absolute path
                if (path == "/") rootDirectory else rootDirectory + path
            } else {
                // Relative path
                if (path == "..") {
                    val parent = File(currentDirectory).parent
                    if (parent != null && parent.startsWith(rootDirectory)) parent else rootDirectory
                } else {
                    "$currentDirectory/$path"
                }
            }

            Log.d(TAG, "Attempting to change directory to: $newPath")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Handle directory navigation using DocumentFile for Android 10+
                if (path == "/" || newPath == rootDirectory) {
                    currentDirectory = rootDirectory
                    currentDocumentFile = rootDocumentFile
                    Log.d(TAG, "Directory changed to root: $currentDirectory")
                    return true
                } else if (path == "..") {
                    val parentDoc = currentDocumentFile?.parentFile
                    if (parentDoc != null && File(currentDirectory).parent?.startsWith(rootDirectory) == true) {
                        currentDirectory = File(currentDirectory).parent ?: rootDirectory
                        currentDocumentFile = parentDoc
                        Log.d(TAG, "Directory changed to parent: $currentDirectory")
                        return true
                    } else {
                        currentDirectory = rootDirectory
                        currentDocumentFile = rootDocumentFile
                        Log.d(TAG, "Directory changed to root (from parent): $currentDirectory")
                        return true
                    }
                } else {
                    // Navigate to child directory
                    val targetName = if (path.contains("/")) path.split("/").last() else path
                    val childDoc = currentDocumentFile?.findFile(targetName)
                    
                    if (childDoc != null && childDoc.isDirectory) {
                        currentDirectory = newPath
                        currentDocumentFile = childDoc
                        Log.d(TAG, "Directory changed to: $currentDirectory using DocumentFile")
                        return true
                    } else {
                        Log.w(TAG, "Failed to change directory: $newPath does not exist or is not a directory (DocumentFile)")
                        return false
                    }
                }
            } else {
                // Legacy file handling for older Android versions
                val dir = File(newPath)
                return if (dir.exists() && dir.isDirectory) {
                    currentDirectory = dir.path
                    Log.d(TAG, "Directory changed to: $currentDirectory")
                    true
                } else {
                    Log.w(TAG, "Failed to change directory: $newPath does not exist or is not a directory")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing directory: ${e.message}")
            return false
        }
    }

    /**
     * List files in the current directory
     * @return List of file entries in FTP format
     */
    fun listFiles(): List<String> {
        try {
            Log.d(TAG, "Listing files in directory: $currentDirectory")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use DocumentFile for Android 10+
                val currentDoc = currentDocumentFile ?: return emptyList()
                val files = currentDoc.listFiles()
                
                Log.d(TAG, "Found ${files.size} files/directories using DocumentFile")
                return files.map { file ->
                    val lastModified = Date(file.lastModified())
                    val size = try { file.length() } catch (e: Exception) { 0L }
                    val permissions = if (file.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                    val dateStr = dateFormat.format(lastModified)
                    
                    "$permissions 1 owner group $size $dateStr ${file.name}"
                }
            } else {
                // Legacy file handling for older Android versions
                val directory = File(currentDirectory)
                val files = directory.listFiles()
                
                if (files == null) {
                    Log.w(TAG, "Failed to list files: directory.listFiles() returned null")
                    return emptyList()
                }
                
                Log.d(TAG, "Found ${files.size} files/directories")
                return files.map { file ->
                    val lastModified = Date(file.lastModified())
                    val size = file.length()
                    val permissions = if (file.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                    val dateStr = dateFormat.format(lastModified)
                    
                    "$permissions 1 owner group $size $dateStr ${file.name}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Create a new directory
     * @param dirName The name of the directory to create
     * @return true if successful, false otherwise
     */
    fun makeDirectory(dirName: String): Boolean {
        try {
            Log.d(TAG, "Attempting to create directory: $dirName in $currentDirectory")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentDoc = currentDocumentFile ?: return false
                val newDir = currentDoc.createDirectory(dirName)
                val result = newDir != null
                
                if (result) {
                    Log.d(TAG, "Directory created successfully using DocumentFile: $dirName")
                } else {
                    Log.w(TAG, "Failed to create directory using DocumentFile: $dirName")
                }
                return result
            } else {
                val newDir = File("$currentDirectory/$dirName")
                val result = newDir.mkdir()
                if (result) {
                    Log.d(TAG, "Directory created successfully: $dirName")
                } else {
                    Log.w(TAG, "Failed to create directory: $dirName")
                }
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory: ${e.message}")
            return false
        }
    }

    /**
     * Delete a file
     * @param fileName The name of the file to delete
     * @return true if successful, false otherwise
     */
    fun deleteFile(fileName: String): Boolean {
        try {
            Log.d(TAG, "Attempting to delete file: $fileName from $currentDirectory")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentDoc = currentDocumentFile ?: return false
                val fileDoc = currentDoc.findFile(fileName) ?: return false
                
                if (!fileDoc.isFile) {
                    Log.w(TAG, "Not a file: $fileName")
                    return false
                }
                
                val result = fileDoc.delete()
                if (result) {
                    Log.d(TAG, "File deleted successfully using DocumentFile: $fileName")
                } else {
                    Log.w(TAG, "Failed to delete file using DocumentFile: $fileName")
                }
                return result
            } else {
                val file = File("$currentDirectory/$fileName")
                
                if (!file.exists()) {
                    Log.w(TAG, "File does not exist: $fileName")
                    return false
                }
                
                if (!file.isFile) {
                    Log.w(TAG, "Not a file: $fileName")
                    return false
                }
                
                val result = file.delete()
                if (result) {
                    Log.d(TAG, "File deleted successfully: $fileName")
                } else {
                    Log.w(TAG, "Failed to delete file: $fileName")
                }
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}")
            return false
        }
    }

    /**
     * Remove a directory
     * @param dirName The name of the directory to remove
     * @return true if successful, false otherwise
     */
    fun removeDirectory(dirName: String): Boolean {
        try {
            Log.d(TAG, "Attempting to remove directory: $dirName from $currentDirectory")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentDoc = currentDocumentFile ?: return false
                val dirDoc = currentDoc.findFile(dirName) ?: return false
                
                if (!dirDoc.isDirectory) {
                    Log.w(TAG, "Not a directory: $dirName")
                    return false
                }
                
                val result = dirDoc.delete()
                if (result) {
                    Log.d(TAG, "Directory removed successfully using DocumentFile: $dirName")
                } else {
                    Log.w(TAG, "Failed to remove directory using DocumentFile: $dirName (may not be empty)")
                }
                return result
            } else {
                val dir = File("$currentDirectory/$dirName")
                
                if (!dir.exists()) {
                    Log.w(TAG, "Directory does not exist: $dirName")
                    return false
                }
                
                if (!dir.isDirectory) {
                    Log.w(TAG, "Not a directory: $dirName")
                    return false
                }
                
                val result = dir.delete()
                if (result) {
                    Log.d(TAG, "Directory removed successfully: $dirName")
                } else {
                    Log.w(TAG, "Failed to remove directory: $dirName (may not be empty)")
                }
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing directory: ${e.message}")
            return false
        }
    }

    /**
     * Get an input stream for a file
     * @param fileName The name of the file to read
     * @return InputStream or null if file doesn't exist
     */
    fun getFileInputStream(fileName: String): InputStream? {
        try {
            Log.d(TAG, "Attempting to get input stream for file: $fileName from $currentDirectory")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentDoc = currentDocumentFile ?: return null
                val fileDoc = currentDoc.findFile(fileName) ?: return null
                
                if (!fileDoc.isFile) {
                    Log.w(TAG, "Not a file: $fileName")
                    return null
                }
                
                val uri = fileDoc.uri
                Log.d(TAG, "Successfully opened input stream for: $fileName using DocumentFile")
                return context.contentResolver.openInputStream(uri)
            } else {
                val file = File("$currentDirectory/$fileName")
                
                if (!file.exists()) {
                    Log.w(TAG, "File does not exist: $fileName")
                    return null
                }
                
                if (!file.isFile) {
                    Log.w(TAG, "Not a file: $fileName")
                    return null
                }
                
                Log.d(TAG, "Successfully opened input stream for: $fileName")
                return FileInputStream(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting input stream: ${e.message}")
            return null
        }
    }

    /**
     * Get an output stream for a file
     * @param fileName The name of the file to write
     * @return OutputStream or null if file can't be created
     */
    fun getFileOutputStream(fileName: String): OutputStream? {
        try {
            Log.d(TAG, "Attempting to get output stream for file: $fileName in $currentDirectory")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentDoc = currentDocumentFile ?: return null
                
                // Check if file already exists
                var fileDoc = currentDoc.findFile(fileName)
                
                // If file doesn't exist, create it
                if (fileDoc == null) {
                    fileDoc = currentDoc.createFile("application/octet-stream", fileName)
                    if (fileDoc == null) {
                        Log.e(TAG, "Failed to create file: $fileName")
                        return null
                    }
                }
                
                val uri = fileDoc.uri
                Log.d(TAG, "Successfully opened output stream for: $fileName using DocumentFile")
                return context.contentResolver.openOutputStream(uri)
            } else {
                val file = File("$currentDirectory/$fileName")
                
                // Create parent directories if they don't exist
                file.parentFile?.mkdirs()
                
                val outputStream = FileOutputStream(file)
                Log.d(TAG, "Successfully opened output stream for: $fileName")
                return outputStream
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting output stream: ${e.message}")
            return null
        }
    }

    /**
     * Rename a file
     * @param oldName The current name of the file
     * @param newName The new name for the file
     * @return true if successful, false otherwise
     */
    fun renameFile(oldName: String, newName: String): Boolean {
        try {
            Log.d(TAG, "Attempting to rename file from: $oldName to: $newName in $currentDirectory")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentDoc = currentDocumentFile ?: return false
                val oldFileDoc = currentDoc.findFile(oldName) ?: return false
                
                // Check if destination file already exists
                val newFileDoc = currentDoc.findFile(newName)
                if (newFileDoc != null) {
                    Log.w(TAG, "Destination file already exists: $newName")
                    // Some FTP clients expect existing files to be overwritten
                    newFileDoc.delete()
                }
                
                // For DocumentFile, we need to use renameTo method
                val result = oldFileDoc.renameTo(newName)
                if (result) {
                    Log.d(TAG, "File renamed successfully using DocumentFile from: $oldName to: $newName")
                } else {
                    Log.w(TAG, "Failed to rename file using DocumentFile from: $oldName to: $newName")
                }
                return result
            } else {
                val oldFile = File("$currentDirectory/$oldName")
                val newFile = File("$currentDirectory/$newName")
                
                if (!oldFile.exists()) {
                    Log.w(TAG, "Source file does not exist: $oldName")
                    return false
                }
                
                if (newFile.exists()) {
                    Log.w(TAG, "Destination file already exists: $newName")
                    // Some FTP clients expect existing files to be overwritten
                    newFile.delete()
                }
                
                val result = oldFile.renameTo(newFile)
                if (result) {
                    Log.d(TAG, "File renamed successfully from: $oldName to: $newName")
                } else {
                    Log.w(TAG, "Failed to rename file from: $oldName to: $newName")
                }
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file: ${e.message}")
            return false
        }
    }

    /**
     * Get file size
     * @param fileName The name of the file
     * @return Size of the file in bytes, or -1 if file doesn't exist
     */
    fun getFileSize(fileName: String): Long {
        try {
            Log.d(TAG, "Getting size for file: $fileName in $currentDirectory")
            
            if (useDocumentFile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val currentDoc = currentDocumentFile ?: return -1
                val fileDoc = currentDoc.findFile(fileName) ?: return -1
                
                if (!fileDoc.isFile) {
                    Log.w(TAG, "Not a file: $fileName")
                    return -1
                }
                
                val size = fileDoc.length()
                Log.d(TAG, "File size for $fileName using DocumentFile: $size bytes")
                return size
            } else {
                val file = File("$currentDirectory/$fileName")
                
                if (!file.exists()) {
                    Log.w(TAG, "File does not exist: $fileName")
                    return -1
                }
                
                if (!file.isFile) {
                    Log.w(TAG, "Not a file: $fileName")
                    return -1
                }
                
                val size = file.length()
                Log.d(TAG, "File size for $fileName: $size bytes")
                return size
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size: ${e.message}")
            return -1
        }
    }
}
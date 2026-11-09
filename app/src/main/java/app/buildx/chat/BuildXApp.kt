package app.buildx.chat

import android.app.Application
import androidx.core.content.FileProvider

class BuildXApp : Application()

class BuildXFileProvider : FileProvider(R.xml.file_paths)

package org.modelix.model.server

import com.beust.jcommander.IStringConverter
import java.io.File

class FileConverter : IStringConverter<File?> {
    override fun convert(value: String?): File? {
        return if (value == null) {
            null
        } else {
            File(value)
        }
    }
}

package hu.mapro.mapping.api

import java.io.{FileInputStream, File}
import java.nio.file.Files
import java.security.{DigestInputStream, MessageDigest}
import resource._

/**
 * Created by marci on 29-09-2015.
 */
object Util {

  def newDisgest = MessageDigest.getInstance("MD5")
  def md5(buf: Array[Byte]): Array[Byte] = newDisgest.digest(buf)
  def md5(file: File): Array[Byte] = {
    val buf = new Array[Byte](4096)
    (for (is <- managed(new DigestInputStream(new FileInputStream(file), newDisgest))) yield {
      Stream.continually(is.read(buf)).takeWhile(_ != -1)
      is.getMessageDigest.digest()
    }).opt.get

  }
  def hex(buf: Array[Byte]): String = buf.map("%02X" format _).mkString
  def hash(buf: Array[Byte]): String = hex(md5(buf))
  def hash(file: File): String = hash(Files.readAllBytes(file.toPath))

}

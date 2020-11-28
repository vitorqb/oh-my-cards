package utils

import java.util.Base64

object Base64Converter {

  def encode(a: Array[Byte]): Array[Byte] = Base64.getEncoder().encode(a)
  def encode(s: String): Array[Byte] = encode(s.getBytes())

  def encodeToString(a: Array[Byte]): String = encode(a).map(_.toChar).mkString
  def encodeToString(s: String): String = encode(s).map(_.toChar).mkString

  def decode(a: Array[Byte]): Array[Byte] = Base64.getDecoder().decode(a)
  def decode(s: String): Array[Byte] = decode(s.getBytes())

  def decodeToString(a: Array[Byte]): String = decode(a).map(_.toChar).mkString
  def decodeToString(s: String): String = decode(s).map(_.toChar).mkString

}

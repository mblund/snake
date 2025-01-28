import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.util.boundary
import scala.util.boundary.break

object Main {
  def main(args: Array[String]): Unit = {
    val (rows, cols) = TerminalUtils.getTerminalSize
    println(s"Terminal size: $rows rows, $cols columns")
  }
}

object TerminalUtils {

  //see https://scala-native.org/en/stable/user/interop.html

  private val STDOUT_FILENO: Int = 1
  type WinSize = CStruct4[UShort, UShort, UShort, UShort]

  def getTerminalSize: (Int, Int) = boundary {
    if (libc.isatty(STDOUT_FILENO) == 0) {
      println("STDOUT_FILENO is not a terminal.")
      break((-1, -1))
    }

    Zone.acquire { implicit z =>
      val winSize: Ptr[WinSize] = alloc[WinSize]()
      // Call ioctl to get terminal size
      val result = libc.ioctl(STDOUT_FILENO, libc.TIOCGWINSZ, winSize)
      if (result != 0) {
        libc.perror(c"ioctl error")
        println(s"ioctl failed with result: $result")
        break((-2, -2))
      }
      // Debug the contents of winsize
      println(s"winsize._1 (rows): ${(!winSize)._1}")
      println(s"winsize._2 (columns): ${(!winSize)._2}")
      val rows = (!winSize)._1.toInt
      val cols = (!winSize)._2.toInt
      (rows, cols)
    }
  }
}

object libc {
  val TIOCGWINSZ: ULong = 0x40087468L.toULong

  @extern
  def ioctl(
      fd: Int,
      request: ULong,
      argp: Ptr[CStruct4[UShort, UShort, UShort, UShort]]
  ): Int = extern
  @extern
  def perror(msg: CString): Unit = extern
  @extern
  def isatty(fd: Int): Int = extern
}

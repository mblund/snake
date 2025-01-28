import scala.scalanative.libc.signal.{SIGINT, signal}
import scala.scalanative.libc.stdlib.exit
import scala.scalanative.posix.signal.SIGTSTP
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.posix.sys.ioctl.*
import scala.scalanative.posix.unistd.*

object BouncingBall {

  def main(args: Array[String]): Unit = {
    registerSignalHandlers()
    runSimulation()
  }

  private val STDOUT_FILENO: Int = 1
  private val TIOCGWINSZ: CLongInt = 0x40087468

  type WinSize = CStruct4[UShort, UShort, UShort, UShort]

  def getTerminalSize: (Int, Int) = {
    val winSize = stackalloc[WinSize]()
    val result =
      ioctl(STDOUT_FILENO, TIOCGWINSZ, winSize.asInstanceOf[Ptr[Byte]])

    if (result == 0) {
      val rows = winSize._1.toInt
      val cols = winSize._2.toInt
      (rows, cols)
    } else {
      (-1, -1) // Error case
    }
  }

  def drawBorder(rows: Int, cols: Int): Unit = {
    val horizontal = "─"
    val vertical = "│"
    val topLeft = "┌"
    val topRight = "┐"
    val bottomLeft = "└"
    val bottomRight = "┘"

    // Move cursor to top-left & draw top border
    print("\u001b[H")
    println(topLeft + horizontal * (cols - 2) + topRight)

    // Draw middle rows
    for (_ <- 2 until rows - 1) {
      println(vertical + " " * (cols - 2) + vertical)
    }

    // Draw bottom border
    println(bottomLeft + horizontal * (cols - 2) + bottomRight)
  }

  def runSimulation(): Unit = {
    hideCursor() // Hide cursor when running simulation

    // Get terminal size and initialize ball position
    val (startCols, startRows) = getTerminalSize
    var x = startCols / 2
    var y = startRows / 2
    var dx = 1
    var dy = 1

    while (true) {
      val (rows, cols) = getTerminalSize

      // Ensure the ball stays inside new terminal bounds
      if (x < 2) x = 2
      if (x >= cols - 1) x = cols - 2
      if (y < 2) y = 2
      if (y >= rows - 1) y = rows - 2

      print("\u001b[2J") // Clear screen
      drawBorder(rows, cols) // Draw border each frame

      // Move cursor and print ball inside borders
      print(s"\u001b[${y};${x}H●")

      // Update position
      x += dx
      y += dy

      // Bounce off the borders
      if (x <= 2 || x >= cols - 2) dx = -dx
      if (y <= 2 || y >= rows - 2) dy = -dy

      System.out.flush()
      usleep(20000.toUInt) // 20ms delay
    }
  }

  /* Hide cursor when running simulation */
  def hideCursor(): Unit = {
    print("\u001b[?25l") // Hide cursor
    System.out.flush()
  }

  /* Restore cursor visibility when exiting */
  def restoreCursor(): Unit = {
    print("\u001b[?25h") // Show cursor again
    System.out.flush()
  }

  /* Register signal handlers for `Ctrl+C` (SIGINT) and `Ctrl+Z` (SIGTSTP) */
  def registerSignalHandlers(): Unit = {
    def signalHandler(sig: CInt): Unit = {
      restoreCursor() // Show cursor when program exits
      exit(0) // Exit program safely
    }

    signal(SIGINT, CFuncPtr1.fromScalaFunction(signalHandler)) // Handle Ctrl+C
    signal(SIGTSTP, CFuncPtr1.fromScalaFunction(signalHandler)) // Handle Ctrl+Z
  }

}

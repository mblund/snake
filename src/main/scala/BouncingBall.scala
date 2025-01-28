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

    print("\u001b[33m") // Set border color to yellow
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
    configureTerminal()
    hideCursor() // Hide cursor when running simulation

    // Get terminal size and initialize ball position
    var (startRows, startCols) = getTerminalSize
    var x = startCols / 2
    var y = startRows / 2
    var dx = 1
    var dy = 1

    drawBorder(startRows, startCols) // Draw border each frame
    while (true) {

      val key = readKey()
      key match {
        case "w" => dy = -1 // Move Up
        case "s" => dy = 1 // Move Down
        case "d" => dx = 1 // Move Right
        case "a" => dx = -1 // Move Left
        case "q" => exitGracefully()
        case _   => // Ignore other keys
      }

      val (rows, cols) = getTerminalSize
      if (rows != startRows || cols != startCols) {
        startCols = cols
        startRows = rows
        drawBorder(rows, cols)
      }

      // Ensure the ball stays inside new terminal bounds
      if (x < 2) x = 2
      if (x >= cols - 1) x = cols - 2
      if (y < 2) y = 2
      if (y >= rows - 1) y = rows - 2

      // Move cursor and print space to erase ball
      print(s"\u001b[${y};${x}H ")

      // Update position
      x += dx
      y += dy

      print("\u001b[31m") // Set ball color to red
      // Move cursor and print ball inside borders
      print(s"\u001b[${y};${x}H●")

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

  def exitGracefully(): Unit = {
    val (_, rows) = getTerminalSize
    print(s"\u001b[$rows;1H")
    print("\u001b[39m") // Reset text color
    print("\u001b[?25h") // Show cursor again
    System.out.flush()
    restoreTerminal()
    println("👋 Exiting...");
    sys.exit(0)
  }

  /* Register signal handlers for `Ctrl+C` (SIGINT) and `Ctrl+Z` (SIGTSTP) */
  def registerSignalHandlers(): Unit = {
    def signalHandler(sig: CInt): Unit = {
      exitGracefully()
    }

    signal(SIGINT, CFuncPtr1.fromScalaFunction(signalHandler)) // Handle Ctrl+C
    signal(SIGTSTP, CFuncPtr1.fromScalaFunction(signalHandler)) // Handle Ctrl+Z
  }

  import scala.scalanative.unsafe.*
  import scala.scalanative.unsigned.*
  import scala.scalanative.posix.termios.*
  import scala.scalanative.posix.fcntl.*
  import scala.scalanative.posix.unistd.*

  def configureTerminal(): Unit = {
    val term = stackalloc[termios]()
    tcgetattr(STDIN_FILENO, term)
    term._4 = term._4 & ~ICANON // Disable line buffering
    term._4 = term._4 & ~ECHO // Disable echo

    tcsetattr(STDIN_FILENO, TCSANOW, term)

    // Set non-blocking read
    val flags = fcntl(STDIN_FILENO, F_GETFL, 0)
    fcntl(STDIN_FILENO, F_SETFL, flags | O_NONBLOCK)
  }

  def restoreTerminal(): Unit = {
    val term = stackalloc[termios]()
    tcgetattr(STDIN_FILENO, term)

    // Enable line buffering & echo again
    term._4 = term._4 | ICANON // Enable line buffering
    term._4 = term._4 | ECHO // Enable character echo

    tcsetattr(STDIN_FILENO, TCSANOW, term)
  }

  def readKey(): String = {
    val buf = stackalloc[Byte]() // Arrow keys use up to 3 bytes
    val n = read(STDIN_FILENO, buf, 3.toCSize)

    if (n > 0) {
      val str = (0 until n).map(i => (!buf + i).toChar).mkString
      str
    } else {
      ""
    }
  }
}

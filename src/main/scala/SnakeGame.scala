import scala.scalanative.libc.signal.{SIGINT, signal}
import scala.scalanative.posix.signal.SIGTSTP
import scala.scalanative.posix.sys.ioctl.*
import scala.scalanative.posix.unistd.*
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.util.Random

object SnakeGame {

  def main(args: Array[String]): Unit = {
    registerSignalHandlers()
    showWelcomeScreen()
    runGame()
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

  def showWelcomeScreen(): Unit = {
    configureTerminal()
    hideCursor() // Hide cursor when running game

    val (rows, cols) = getTerminalSize
    clearScreen()
    drawBorder(rows, cols)

    val welcomeMessage = "Welcome to Snake!"
    val promptMessage = "Use a,s,d,w to move"
    val promptMessage2 = "Press SPACE to play"
    val welcomeRow = rows / 2 - 1
    val promptRow = rows / 2 + 3
    val promptRow2 = rows / 2 + 5
    val welcomeCol = (cols - welcomeMessage.length) / 2
    val promptCol = (cols - promptMessage.length) / 2
    val promptCol2 = (cols - promptMessage2.length) / 2

    print(s"\u001b[${welcomeRow};${welcomeCol}H$welcomeMessage")
    print(s"\u001b[${promptRow};${promptCol}H$promptMessage")
    print(s"\u001b[${promptRow2};${promptCol2}H$promptMessage2")
    System.out.flush()

    // Wait for space key press
    var key = ""
    while (key != " ") {
      key = readKey()
      usleep(10000.toUInt) // 10ms delay to prevent busy waiting
    }

    showCursor() // Show cursor again
  }

  def runGame(): Unit = {
    clearScreen()
    configureTerminal()
    hideCursor() // Hide cursor when running game

    // Get terminal size and initialize snake position
    var (startRows, startCols) = getTerminalSize
    var snake =
      List((startCols / 2, startRows / 2), (startCols / 2 - 1, startRows / 2))
    var direction = (1, 0)
    var food = placeFood(startRows, startCols, snake)

    drawBorder(startRows, startCols)
    while (true) {

      val key = readKey()
      key match {
        case "w" if direction != (0, 1)  => direction = (0, -1) // Move Up
        case "s" if direction != (0, -1) => direction = (0, 1) // Move Down
        case "d" if direction != (-1, 0) => direction = (1, 0) // Move Right
        case "a" if direction != (1, 0)  => direction = (-1, 0) // Move Left
        case "q"                         => exitGracefully()
        case _                           => // Ignore other keys
      }

      val (rows, cols) = getTerminalSize
      if (rows != startRows || cols != startCols) {
        startCols = cols
        startRows = rows
        clearScreen()
        drawBorder(rows, cols)
      }

      // Update snake position
      val newHead = (snake.head._1 + direction._1, snake.head._2 + direction._2)
      snake = newHead :: snake

      // Check for collisions
      if (
        newHead._1 <= 1 || newHead._1 >= cols - 1 || newHead._2 <= 1 || newHead._2 >= rows - 1 || snake.tail
          .contains(newHead)
      ) {
        showGameOverScreen()
      }

      // Check if snake eats food
      if (newHead == food) {
        food = placeFood(rows, cols, snake)
      } else {
        val tail = snake.last
        print(s"\u001b[${tail._2};${tail._1}H ")
        snake = snake.init
      }

      // Draw snake and food
      print("\u001b[32m") // Set snake color to green
      snake.foreach { case (x, y) => print(s"\u001b[${y};${x}H●") }
      print("\u001b[31m") // Set food color to red
      print(s"\u001b[${food._2};${food._1}H●")

      System.out.flush()
      usleep(100000.toUInt) // 100ms delay
    }
  }

  def placeFood(rows: Int, cols: Int, snake: List[(Int, Int)]): (Int, Int) = {
    val rand = new Random()
    var food = (rand.nextInt(cols - 2) + 1, rand.nextInt(rows - 2) + 1)
    while (
      snake.contains(
        food
      ) || food._1 <= 1 || food._1 >= cols - 1 || food._2 <= 1 || food._2 >= rows - 1
    ) {
      food = (rand.nextInt(cols - 2) + 1, rand.nextInt(rows - 2) + 1)
    }
    food
  }

  /* Hide cursor when running game */
  def hideCursor(): Unit = {
    print("\u001b[?25l") // Hide cursor
    System.out.flush()
  }

  def showCursor(): Unit = {
    print("\u001b[?25h") // Show cursor
    System.out.flush()
  }

  def clearScreen(): Unit = {
    print("\u001b[2J") // Clear screen
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

  import scala.scalanative.posix.fcntl.*
  import scala.scalanative.posix.termios.*
  import scala.scalanative.posix.unistd.*
  import scala.scalanative.unsafe.*
  import scala.scalanative.unsigned.*

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

  def showGameOverScreen(): Unit = {
    val (rows, cols) = getTerminalSize
    clearScreen()
    drawBorder(rows, cols)

    val gameOverMessage = "Game Over!"
    val promptMessage = "Press SPACE to play again"
    val promptMessage2 = "(q to quit)"
    val gameOverRow = rows / 2 - 1
    val promptRow = rows / 2 + 1
    val promptRow2 = rows / 2 + 2
    val gameOverCol = (cols - gameOverMessage.length) / 2
    val promptCol = (cols - promptMessage.length) / 2
    val promptCol2 = (cols - promptMessage2.length) / 2

    print(s"\u001b[${gameOverRow};${gameOverCol}H$gameOverMessage")
    print(s"\u001b[${promptRow};${promptCol}H$promptMessage")
    print(s"\u001b[${promptRow2};${promptCol2}H$promptMessage2")
    System.out.flush()

    // Wait for space key press or 'q' to quit
    var key = ""
    while (key != " " && key != "q") {
      key = readKey()
      usleep(10000.toUInt) // 10ms delay to prevent busy waiting
    }

    if (key == "q") {
      exitGracefully()
    } else {
      clearScreen()
      runGame()
    }
  }
}



Unable to get terminal size in Scala Native
```
> sbt nativeLink
> /target/scala-3.3.3/scalanativecli2
winsize._1 (rows): 0
winsize._2 (columns): 0
Terminal size: 0 rows, 0 columns
```

When I run the same code in C, it works fine:
```
> gcc get-terminal-size.c -o get-terminal-size
> ./get-terminal-size 
TIOCGWINSZ: 40087468
Rows: 33, Columns: 106
```
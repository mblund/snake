# Makefile for the c-experiment folder

# Compiler
CC = gcc

# Source file
SRC = get-terminal-size.c

# Output binary
OUT = get-terminal-size

# Default target
all: $(OUT)

# Compile the source file
$(OUT): $(SRC)
	$(CC) $(SRC) -o $(OUT)

# Run the compiled binary
run: $(OUT)
	./$(OUT)

# Clean target to remove the compiled binary
clean:
	rm -f $(OUT)
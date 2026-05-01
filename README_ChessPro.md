# ChessPro - Ultimate Coach Edition

ChessPro is a single-file Java chess program with a Swing GUI, legal chess rules, multiple playing modes, several AI levels, an opening book, hints, FEN support, PGN-like export, game review, and a simple local Elo system.

This version uses an external opening-book text file:

```text
chesspro_openings.txt
```

The program can still run without the opening-book file, but the opening-book feature works best when the text file is placed in the correct folder.

---

## 1. Project structure

Recommended folder structure:

```text
ChessPro/
│
├── src/
│   └── ChessPro.java
│
├── chesspro_openings.txt
│
├── README.md
│
└── out/
    └── generated .class files after compiling
```

If your `ChessPro.java` is directly in the main folder instead of `src`, this is also fine:

```text
ChessPro/
│
├── ChessPro.java
├── chesspro_openings.txt
├── README.md
└── out/
```

Important:

`chesspro_openings.txt` should be in the working directory, usually the main project folder. In IntelliJ, this is normally the project root folder.

---

## 2. Requirements

You need:

- Java JDK 17 or newer recommended
- IntelliJ IDEA, Eclipse, VS Code, or normal command line
- No external libraries are required

The program only uses standard Java libraries such as Swing, AWT, IO, NIO, Collections, and Properties.

---

## 3. How to compile and run

### Option A: If `ChessPro.java` is in the main folder

Open CMD/Terminal in the project folder:

```bat
javac ChessPro.java
java ChessPro
```

### Option B: If `ChessPro.java` is in `src`

Open CMD/Terminal in the project folder:

```bat
javac src\ChessPro.java -d out
java -cp out ChessPro
```

On macOS/Linux:

```bash
javac src/ChessPro.java -d out
java -cp out ChessPro
```

---

## 4. IntelliJ setup

A good IntelliJ structure is:

```text
ChessPro/
│
├── .idea/
├── out/
├── src/
│   └── ChessPro.java
└── chesspro_openings.txt
```

Steps:

1. Open the project folder in IntelliJ.
2. Put `ChessPro.java` inside `src`.
3. Put `chesspro_openings.txt` directly in the project root folder, not inside `src`.
4. Run `ChessPro.main()`.

If the opening book does not load, check the Run Configuration and make sure the working directory is the project folder.

---

## 5. Opening book file

The external opening book file is:

```text
chesspro_openings.txt
```

Each line has this format:

```text
move move move move | Opening name
```

Example:

```text
e2e4 e7e5 g1f3 b8c6 f1b5 | Ruy Lopez
d2d4 d7d5 c2c4 | Queen's Gambit
e2e4 c7c5 g1f3 d7d6 | Sicilian Defense
```

Rules:

- Use coordinate notation.
- Use one opening line per row.
- Separate the move sequence and name with `|`.
- Empty lines are allowed.
- Lines beginning with `#` can be used as comments.
- Do not add Java quotes.
- Do not add commas.
- Do not add `String[]`, `{}`, or Java text-block markers.

Correct:

```text
e2e4 e7e5 g1f3 b8c6 | Four Knights style line
```

Wrong:

```java
"e2e4 e7e5 g1f3 b8c6 | Four Knights style line",
```

---

## 6. How the opening book works

When it is the AI's turn, the program first checks whether the opening book option is enabled.

If the current move history matches the beginning of one or more opening lines, the program chooses a matching next book move.

Example:

Opening-book line:

```text
e2e4 e7e5 g1f3 b8c6 | Open Game
```

If the game so far is:

```text
e2e4 e7e5
```

then the next book move is:

```text
g1f3
```

If several lines match the current game, the program can choose one of the matching candidate moves.

If no book move exists, the normal chess AI search chooses the move.

---

## 7. Playing modes

ChessPro supports four modes:

```text
Human vs Human
Human vs AI
AI vs Human
AI vs AI
```

Human vs Human lets two people play on the same computer.

Human vs AI lets you play White against the computer.

AI vs Human lets the computer play White and you play Black.

AI vs AI lets both sides be controlled by the engine.

---

## 8. Chess rules supported

The board logic supports normal legal chess move generation, including:

- normal piece movement
- captures
- check
- checkmate
- stalemate
- castling
- en passant
- pawn promotion
- fifty-move rule
- insufficient material detection

Moves that leave your own king in check are rejected.

---

## 9. AI levels

The AI levels are configured with four main values:

```text
label
search depth
randomness percentage
quiescence search on/off
node limit
```

Example:

```text
Grandmaster 2600
depth: 8
randomness: 0%
quiescence: true
node limit: 4,500,000
```

Lower AI levels intentionally make weaker or more random decisions. Stronger levels search deeper, use less randomness, and use more nodes.

---

## 10. How the AI chooses a move

When the AI has to move, the general process is:

```text
1. Generate all legal moves.
2. If an opening-book move exists, play it.
3. Otherwise, analyze all legal moves.
4. Search possible replies and counter-replies.
5. Evaluate the resulting positions.
6. Choose the move with the best score.
```

The main search uses a negamax-style algorithm with alpha-beta pruning.

The engine also uses:

- iterative deepening
- move ordering
- transposition table
- killer move heuristic
- history heuristic
- quiescence search
- material evaluation
- positional evaluation
- king safety
- pawn structure evaluation
- mobility evaluation
- threat evaluation
- space evaluation

---

## 11. Why Grandmaster can take long

Grandmaster is slow because it searches deeply.

A depth of 8 means the engine tries to look many half-moves ahead. In chess, the number of possible move sequences grows very quickly. Even with alpha-beta pruning, move ordering, and other optimizations, this can still require millions of searched positions.

The Grandmaster setting is designed to be strong, not instant.

If Grandmaster is too slow, reduce either the depth or node limit.

Example faster Grandmaster:

```java
GRANDMASTER("Grandmaster 2600", 7, 0, true, 2_500_000),
```

Even faster:

```java
GRANDMASTER("Grandmaster 2600", 6, 0, true, 1_500_000),
```

---

## 12. Can the AI be stronger than Grandmaster?

Yes, technically it is possible to add a stronger AI than Grandmaster.

Simple version:

```java
SUPER_GRANDMASTER("Super Grandmaster 2850", 9, 0, true, 8_000_000),
```

or:

```java
SUPER_GRANDMASTER("Super Grandmaster 2850", 10, 0, true, 15_000_000),
```

However, this will be much slower.

To make the AI truly stronger without becoming unusably slow, the engine should be optimized. The most important improvements would be:

1. Use make/unmake moves instead of copying the whole board every search node.
2. Use Zobrist hashing instead of FEN strings for the transposition table.
3. Improve move ordering.
4. Add better evaluation tables.
5. Add an endgame tablebase or simpler endgame knowledge.
6. Add time management instead of only fixed depth and node limits.
7. Add null-move pruning.
8. Add late move reductions.
9. Add principal variation search.
10. Add aspiration windows.

A stronger AI is possible, but just increasing depth from 8 to 10 can make the engine much slower.

---

## 13. Game review

The game review analyzes the moves played in the game and compares them to the engine's preferred moves.

It classifies moves into categories such as:

```text
Brilliant
Best
Excellent
Good
Inaccuracy
Mistake
Blunder
```

The review is useful for learning, but it is still based on this local Java engine. It is not the same as Stockfish.

---

## 14. FEN support

FEN is a compact text format for saving and loading chess positions.

ChessPro can:

- copy the current FEN
- load a FEN position
- continue playing from that position

This is useful for testing special positions, puzzles, checkmates, endgames, or positions from real games.

---

## 15. Undo and redo

The program stores game states so you can undo and redo moves.

This helps when testing the AI, checking variations, or correcting a wrong move.

---

## 16. Elo system

ChessPro has a simple local Elo system.

The ratings are saved in:

```text
chesspro_elo.properties
```

This file is stored in your user home directory.

The Elo system is local and only meant for fun. It is not an official rating.

---

## 17. Themes and GUI features

The GUI includes several quality-of-life features:

- different board themes
- board flipping
- coordinate display
- legal move highlights
- capture highlights
- last move highlight
- hint move
- status information
- move list
- evaluation information
- game review output

---

## 18. Troubleshooting

### Opening book does not work

Check that this file exists:

```text
chesspro_openings.txt
```

It must be in the working directory.

For IntelliJ, put it in the project root folder.

### Program starts but no AI move happens

Check:

- Is the game mode set to a mode with AI?
- Is the game already over?
- Is it actually the AI's turn?
- Did you choose an AI level?

### Grandmaster is too slow

Reduce the depth or node limit:

```java
GRANDMASTER("Grandmaster 2600", 7, 0, true, 2_500_000),
```

or choose a weaker AI level.

### Java cannot find the main class

If using `src` and `out`, run:

```bat
javac src\ChessPro.java -d out
java -cp out ChessPro
```

---

## 19. Recommended files to submit or share

For a clean project submission, include:

```text
ChessPro.java
chesspro_openings.txt
README.md
```

If using IntelliJ, your folder can look like this:

```text
ChessPro/
│
├── src/
│   └── ChessPro.java
│
├── chesspro_openings.txt
├── README.md
└── out/
```

You usually do not need to submit `.class` files, because they can be regenerated by compiling the Java source file.

---

## 20. Summary

ChessPro is a full Java chess program with a playable GUI, legal chess rules, multiple AI levels, opening-book support, hints, review features, and a simple Elo system.

The external `chesspro_openings.txt` file keeps the Java source code cleaner and makes the opening book easier to edit.

Grandmaster is currently the strongest practical built-in mode, but an even stronger mode can be added by increasing depth and node limits. For a truly stronger engine, performance optimizations are more important than simply making the search deeper.

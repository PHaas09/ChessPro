import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for ChessPro.java.
 *
 * Put this file next to ChessPro.java or into your test source folder.
 *
 * Example IntelliJ / simple project structure:
 *
 * ChessPro/
 * ├── src/
 * │   ├── ChessPro.java
 * │   └── ChessProTest.java
 * └── chesspro_openings.txt
 *
 * If you use Maven or Gradle, put this file into:
 *
 * src/test/java/ChessProTest.java
 *
 * Important:
 * - ChessPro.java has no package declaration, so this test class also has no package declaration.
 * - These tests use JUnit 5.
 */
class ChessProTest {

    @BeforeAll
    static void enableHeadlessMode() {
        System.setProperty("java.awt.headless", "true");
    }

    private static int sq(String square) {
        return Board.squareFromName(square);
    }

    private static String coord(Move move) {
        return move == null ? null : move.coordinate();
    }

    private static List<String> coordinates(Board board) {
        return board.legalMoves().stream()
                .map(Move::coordinate)
                .collect(Collectors.toList());
    }

    private static Move legalMove(Board board, String coordinate) {
        assertNotNull(board, "Board must not be null.");
        assertNotNull(coordinate, "Coordinate move must not be null.");
        assertTrue(coordinate.length() >= 4, "Coordinate move must have at least four characters.");

        int from = Board.squareFromName(coordinate.substring(0, 2));
        int to = Board.squareFromName(coordinate.substring(2, 4));
        assertTrue(from >= 0, "Invalid from-square in coordinate: " + coordinate);
        assertTrue(to >= 0, "Invalid to-square in coordinate: " + coordinate);

        int promotion = 0;
        if (coordinate.length() >= 5) {
            char ch = Character.toLowerCase(coordinate.charAt(4));
            if (ch == 'q') promotion = board.turn.sign * Piece.QUEEN;
            else if (ch == 'r') promotion = board.turn.sign * Piece.ROOK;
            else if (ch == 'b') promotion = board.turn.sign * Piece.BISHOP;
            else if (ch == 'n') promotion = board.turn.sign * Piece.KNIGHT;
            else fail("Unsupported promotion piece in coordinate: " + coordinate);
        }

        Move move = board.findMatchingLegal(new Move(from, to, promotion));
        assertNotNull(move, () -> "Expected legal move " + coordinate + " in position:\n"
                + board.toFen() + "\nLegal moves: " + coordinates(board));
        return move;
    }

    private static boolean hasLegalMove(Board board, String coordinate) {
        int from = Board.squareFromName(coordinate.substring(0, 2));
        int to = Board.squareFromName(coordinate.substring(2, 4));
        int promotion = 0;
        if (coordinate.length() >= 5) {
            char ch = Character.toLowerCase(coordinate.charAt(4));
            if (ch == 'q') promotion = board.turn.sign * Piece.QUEEN;
            else if (ch == 'r') promotion = board.turn.sign * Piece.ROOK;
            else if (ch == 'b') promotion = board.turn.sign * Piece.BISHOP;
            else if (ch == 'n') promotion = board.turn.sign * Piece.KNIGHT;
        }
        return board.findMatchingLegal(new Move(from, to, promotion)) != null;
    }

    private static Move play(Board board, String coordinate) {
        Move move = legalMove(board, coordinate);
        assertTrue(board.makeUserMove(move), "makeUserMove should accept legal move " + coordinate);
        return move;
    }

    @Nested
    @DisplayName("Board coordinates, pieces and FEN")
    class BoardAndFenTests {

        @Test
        @DisplayName("Square names and indexes are converted correctly")
        void squareNameAndIndexConversionWorks() {
            assertAll(
                    () -> assertEquals("a8", Board.squareName(0)),
                    () -> assertEquals("h8", Board.squareName(7)),
                    () -> assertEquals("a1", Board.squareName(56)),
                    () -> assertEquals("h1", Board.squareName(63)),
                    () -> assertEquals(Board.idx(6, 4), Board.squareFromName("e2")),
                    () -> assertEquals(Board.idx(0, 4), Board.squareFromName("e8")),
                    () -> assertEquals(-1, Board.squareFromName("i9")),
                    () -> assertEquals(-1, Board.squareFromName(null))
            );
        }

        @Test
        @DisplayName("Piece helper methods return expected values")
        void pieceHelpersWork() {
            assertAll(
                    () -> assertTrue(Piece.isWhite(Piece.QUEEN)),
                    () -> assertTrue(Piece.isBlack(-Piece.KNIGHT)),
                    () -> assertEquals(Piece.BISHOP, Piece.type(-Piece.BISHOP)),
                    () -> assertEquals(Side.WHITE, Piece.side(Piece.PAWN)),
                    () -> assertEquals(Side.BLACK, Piece.side(-Piece.PAWN)),
                    () -> assertEquals('Q', Piece.fenChar(Piece.QUEEN)),
                    () -> assertEquals('q', Piece.fenChar(-Piece.QUEEN)),
                    () -> assertEquals(Piece.KING, Piece.fromFenChar('K')),
                    () -> assertEquals(-Piece.KING, Piece.fromFenChar('k')),
                    () -> assertEquals("N", Piece.letter(Piece.KNIGHT)),
                    () -> assertEquals("Queen", Piece.name(Piece.QUEEN)),
                    () -> assertEquals(900, Piece.value(Piece.QUEEN)),
                    () -> assertEquals("", Piece.unicode(Piece.EMPTY))
            );
        }

        @Test
        @DisplayName("Starting board has the exact normal starting FEN")
        void startingFenIsCorrect() {
            Board board = Board.starting();

            assertEquals(
                    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    board.toFen()
            );
            assertEquals(Side.WHITE, board.turn);
            assertEquals(1, board.fenHistory.size());
        }

        @Test
        @DisplayName("Starting position has exactly 20 legal moves")
        void startingPositionHasTwentyLegalMoves() {
            Board board = Board.starting();

            List<String> moves = coordinates(board);

            assertEquals(20, moves.size());
            assertTrue(moves.contains("e2e4"));
            assertTrue(moves.contains("g1f3"));
            assertFalse(moves.contains("e1e2"));
        }

        @Test
        @DisplayName("FEN round-trip keeps the same position")
        void fenRoundTripWorks() {
            String fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3";

            Board board = Board.fromFen(fen);

            assertEquals(fen, board.toFen());
            assertEquals(Side.WHITE, board.turn);
            assertEquals(Piece.KNIGHT, board.sq[sq("f3")]);
            assertEquals(-Piece.KNIGHT, board.sq[sq("c6")]);
        }

        @Test
        @DisplayName("Invalid FEN strings throw exceptions")
        void invalidFenThrowsException() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> Board.fromFen(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> Board.fromFen("8/8/8/8/8/8/8 w - - 0 1")),
                    () -> assertThrows(IllegalArgumentException.class, () -> Board.fromFen("8/8/8/8/8/8/8/8 w - nope 0 1")),
                    () -> assertThrows(IllegalArgumentException.class, () -> Board.fromFen("8/8/8/8/8/8/8/X7 w - - 0 1"))
            );
        }

        @Test
        @DisplayName("Board copy is independent from the original board")
        void boardCopyIsIndependent() {
            Board original = Board.starting();
            Board copy = original.copy();

            play(copy, "e2e4");

            assertEquals(
                    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    original.toFen()
            );
            assertEquals(
                    "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                    copy.toFen()
            );
        }

        @Test
        @DisplayName("Material label reports equal material in the starting position")
        void materialLabelReportsEqualAtStart() {
            assertEquals("Material: equal", Board.starting().materialLabel());
        }

        @Test
        @DisplayName("Material label reports white advantage when black queen is missing")
        void materialLabelReportsWhiteAdvantage() {
            Board board = Board.fromFen("rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

            assertEquals("Material: White +9", board.materialLabel());
        }
    }

    @Nested
    @DisplayName("Move making and normal chess rules")
    class MoveMakingTests {

        @Test
        @DisplayName("A normal pawn double move updates turn, en-passant square and FEN")
        void pawnDoubleMoveUpdatesFen() {
            Board board = Board.starting();

            Move move = play(board, "e2e4");

            assertEquals("e2e4", move.coordinate());
            assertEquals("e4", move.toString());
            assertEquals(Side.BLACK, board.turn);
            assertEquals(sq("e3"), board.epSquare);
            assertEquals(0, board.halfmoveClock);
            assertEquals(1, board.fullmoveNumber);
            assertEquals(
                    "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                    board.toFen()
            );
        }

        @Test
        @DisplayName("Fullmove number increments after Black moves")
        void fullmoveNumberIncrementsAfterBlackMove() {
            Board board = Board.starting();

            play(board, "e2e4");
            assertEquals(1, board.fullmoveNumber);

            play(board, "e7e5");
            assertEquals(2, board.fullmoveNumber);
            assertEquals(Side.WHITE, board.turn);
        }

        @Test
        @DisplayName("Halfmove clock increments on quiet moves and resets on pawn moves")
        void halfmoveClockIsUpdatedCorrectly() {
            Board board = Board.starting();

            play(board, "g1f3");
            assertEquals(1, board.halfmoveClock);

            play(board, "g8f6");
            assertEquals(2, board.halfmoveClock);

            play(board, "e2e4");
            assertEquals(0, board.halfmoveClock);
        }

        @Test
        @DisplayName("Illegal moves are rejected")
        void illegalMovesAreRejected() {
            Board board = Board.starting();

            assertFalse(board.makeUserMove(new Move(sq("e2"), sq("e5"))));
            assertFalse(board.makeUserMove(new Move(sq("e1"), sq("e2"))));
            assertFalse(board.makeUserMove(new Move(sq("a1"), sq("a4"))));
            assertEquals(0, board.history.size());
            assertEquals(Side.WHITE, board.turn);
        }

        @Test
        @DisplayName("makeMoveNoHistory changes the board but does not add history")
        void makeMoveNoHistoryDoesNotAddHistory() {
            Board board = Board.starting();
            Move move = legalMove(board, "e2e4");

            board.makeMoveNoHistory(move);

            assertEquals(0, board.history.size());
            assertEquals(1, board.fenHistory.size());
            assertEquals(Side.BLACK, board.turn);
            assertEquals(Piece.PAWN, board.sq[sq("e4")]);
        }

        @Test
        @DisplayName("Captured pieces are removed from the board")
        void capturesRemoveCapturedPiece() {
            Board board = Board.starting();

            play(board, "e2e4");
            play(board, "d7d5");
            Move capture = play(board, "e4d5");

            assertEquals("e4d5", capture.coordinate());
            assertEquals(Piece.PAWN, board.sq[sq("d5")]);
            assertEquals(Piece.EMPTY, board.sq[sq("e4")]);
            assertEquals(-Piece.PAWN, capture.capturedPiece);
        }

        @Test
        @DisplayName("Pawn promotion creates four legal promotion moves")
        void promotionCreatesFourLegalMoves() {
            Board board = Board.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");

            List<String> moves = coordinates(board);

            assertTrue(moves.contains("a7a8q"));
            assertTrue(moves.contains("a7a8r"));
            assertTrue(moves.contains("a7a8b"));
            assertTrue(moves.contains("a7a8n"));
        }

        @Test
        @DisplayName("Promotion to queen places a queen on the promotion square")
        void promotionToQueenPlacesQueen() {
            Board board = Board.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");

            Move move = play(board, "a7a8q");

            assertEquals(Piece.QUEEN, board.sq[sq("a8")]);
            assertEquals(Piece.EMPTY, board.sq[sq("a7")]);
            assertEquals(Piece.QUEEN, move.promotion);
        }

        @Test
        @DisplayName("En-passant move is generated and removes the captured pawn")
        void enPassantWorks() {
            Board board = Board.starting();

            play(board, "e2e4");
            play(board, "a7a6");
            play(board, "e4e5");
            play(board, "d7d5");

            Move enPassant = legalMove(board, "e5d6");

            assertTrue(enPassant.enPassant);
            play(board, "e5d6");

            assertEquals(Piece.PAWN, board.sq[sq("d6")]);
            assertEquals(Piece.EMPTY, board.sq[sq("e5")]);
            assertEquals(Piece.EMPTY, board.sq[sq("d5")]);
        }
    }

    @Nested
    @DisplayName("Castling rules")
    class CastlingTests {

        @Test
        @DisplayName("White can castle both sides when the path is clear and safe")
        void whiteCanCastleBothSides() {
            Board board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

            Move kingSide = legalMove(board, "e1g1");
            Move queenSide = legalMove(board, "e1c1");

            assertTrue(kingSide.castleKingSide);
            assertTrue(queenSide.castleQueenSide);
            assertEquals("O-O", kingSide.toString());
            assertEquals("O-O-O", queenSide.toString());
        }

        @Test
        @DisplayName("White king-side castling moves king and rook correctly")
        void whiteKingSideCastleMovesBothPieces() {
            Board board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

            play(board, "e1g1");

            assertEquals(Piece.KING, board.sq[sq("g1")]);
            assertEquals(Piece.ROOK, board.sq[sq("f1")]);
            assertEquals(Piece.EMPTY, board.sq[sq("e1")]);
            assertEquals(Piece.EMPTY, board.sq[sq("h1")]);
            assertFalse(board.whiteKingCastle);
            assertFalse(board.whiteQueenCastle);
        }

        @Test
        @DisplayName("Black king-side castling moves king and rook correctly")
        void blackKingSideCastleMovesBothPieces() {
            Board board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1");

            play(board, "e8g8");

            assertEquals(-Piece.KING, board.sq[sq("g8")]);
            assertEquals(-Piece.ROOK, board.sq[sq("f8")]);
            assertEquals(Piece.EMPTY, board.sq[sq("e8")]);
            assertEquals(Piece.EMPTY, board.sq[sq("h8")]);
            assertFalse(board.blackKingCastle);
            assertFalse(board.blackQueenCastle);
        }

        @Test
        @DisplayName("Castling is not allowed while the king is in check")
        void cannotCastleWhileInCheck() {
            Board board = Board.fromFen("4r1k1/8/8/8/8/8/8/R3K2R w KQ - 0 1");

            assertTrue(board.isInCheck(Side.WHITE));
            assertFalse(hasLegalMove(board, "e1g1"));
            assertFalse(hasLegalMove(board, "e1c1"));
        }

        @Test
        @DisplayName("Castling is not allowed through an attacked square")
        void cannotCastleThroughAttackedSquare() {
            Board board = Board.fromFen("r3k2r/8/8/8/8/5r2/8/R3K2R w KQkq - 0 1");

            assertFalse(hasLegalMove(board, "e1g1"));
        }

        @Test
        @DisplayName("Moving a rook removes the related castling right")
        void movingRookRemovesCastlingRight() {
            Board board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

            play(board, "h1h2");

            assertFalse(board.whiteKingCastle);
            assertTrue(board.whiteQueenCastle);
        }

        @Test
        @DisplayName("Capturing a rook removes the opponent's castling right")
        void capturingRookRemovesOpponentCastlingRight() {
            Board board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

            play(board, "a1a8");

            assertFalse(board.blackQueenCastle);
            assertTrue(board.blackKingCastle);
        }
    }

    @Nested
    @DisplayName("Check, checkmate, stalemate and draw rules")
    class CheckAndResultTests {

        @Test
        @DisplayName("A rook gives check along an open file")
        void rookGivesCheckAlongFile() {
            Board board = Board.fromFen("4k3/8/8/8/8/8/4R3/4K3 b - - 0 1");

            assertTrue(board.isInCheck(Side.BLACK));
            assertTrue(board.isAttacked(sq("e8"), Side.WHITE));
        }

        @Test
        @DisplayName("Legal moves may not leave the own king in check")
        void legalMovesDoNotLeaveKingInCheck() {
            Board board = Board.fromFen("4k3/8/8/8/8/8/4R3/4K3 b - - 0 1");

            for (Move move : board.legalMoves()) {
                Board next = board.copy();
                next.makeMoveNoHistory(move);
                assertFalse(next.isInCheck(Side.BLACK), "Illegal self-check move was generated: " + move.coordinate());
            }
        }

        @Test
        @DisplayName("Fool's mate is detected as checkmate for Black")
        void foolsMateIsDetected() {
            Board board = Board.starting();

            play(board, "f2f3");
            play(board, "e7e5");
            play(board, "g2g4");
            play(board, "d8h4");

            assertTrue(board.isGameOver());
            assertTrue(board.isInCheck(Side.WHITE));
            assertEquals(0, board.legalMoves().size());
            assertEquals("0-1 checkmate", board.resultString());
            assertEquals(0.0, board.scoreForWhite());
        }

        @Test
        @DisplayName("A known stalemate position is detected correctly")
        void stalemateIsDetected() {
            Board board = Board.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");

            assertTrue(board.isGameOver());
            assertFalse(board.isInCheck(Side.BLACK));
            assertEquals(0, board.legalMoves().size());
            assertEquals("1/2-1/2 stalemate", board.resultString());
            assertEquals(0.5, board.scoreForWhite());
        }

        @Test
        @DisplayName("King versus king is insufficient material")
        void kingVersusKingIsInsufficientMaterial() {
            Board board = Board.fromFen("8/8/8/8/8/8/4K3/4k3 w - - 0 1");

            assertTrue(board.insufficientMaterial());
            assertTrue(board.isGameOver());
            assertEquals("1/2-1/2 by insufficient material", board.resultString());
        }

        @Test
        @DisplayName("King and rook versus king is not insufficient material")
        void rookMeansMaterialIsSufficient() {
            Board board = Board.fromFen("8/8/8/8/8/8/4K3/R3k3 w - - 0 1");

            assertFalse(board.insufficientMaterial());
        }

        @Test
        @DisplayName("Fifty-move rule is detected")
        void fiftyMoveRuleIsDetected() {
            Board board = Board.fromFen("8/8/8/8/8/8/4K3/R3k3 w - - 100 1");

            assertTrue(board.isGameOver());
            assertEquals("1/2-1/2 by fifty-move rule", board.resultString());
        }
    }

    @Nested
    @DisplayName("Perft and move generation counts")
    class PerftTests {

        @Test
        @DisplayName("Perft depth 1 from starting position is 20")
        void perftDepthOneStartPosition() {
            assertEquals(20, Perft.nodes(Board.starting(), 1));
        }

        @Test
        @DisplayName("Perft depth 2 from starting position is 400")
        @Timeout(5)
        void perftDepthTwoStartPosition() {
            assertEquals(400, Perft.nodes(Board.starting(), 2));
        }

        @Test
        @DisplayName("Perft divide output contains a total")
        void perftDivideContainsTotal() {
            String divide = Perft.divide(Board.starting(), 1);

            assertTrue(divide.contains("Total: 20"));
            assertTrue(divide.contains("e2e4"));
            assertTrue(divide.contains("g1f3"));
        }
    }

    @Nested
    @DisplayName("Opening book from external txt data")
    class OpeningBookTests {
        private List<OpeningBook.BookLine> savedLines;

        @BeforeEach
        void saveAndClearOpeningBook() {
            savedLines = new ArrayList<>(OpeningBook.LINES);
            OpeningBook.LINES.clear();
        }

        @AfterEach
        void restoreOpeningBook() {
            OpeningBook.LINES.clear();
            OpeningBook.LINES.addAll(savedLines);
        }

        @Test
        @DisplayName("parseBookRow ignores empty lines and comments")
        void parseBookRowIgnoresCommentsAndEmptyLines() {
            OpeningBook.parseBookRow("");
            OpeningBook.parseBookRow("   ");
            OpeningBook.parseBookRow("# comment");
            OpeningBook.parseBookRow("e2e4 e7e5 without bar");

            assertEquals(0, OpeningBook.LINES.size());
        }

        @Test
        @DisplayName("parseBookRow adds a valid book line")
        void parseBookRowAddsValidLine() {
            OpeningBook.parseBookRow("e2e4 e7e5 g1f3 | Test Opening");

            assertEquals(1, OpeningBook.LINES.size());
            assertArrayEquals(new String[]{"e2e4", "e7e5", "g1f3"}, OpeningBook.LINES.get(0).moves);
            assertEquals("Test Opening", OpeningBook.LINES.get(0).name);
        }

        @Test
        @DisplayName("Opening book chooses the first move from a matching line")
        void openingBookChoosesFirstMove() {
            OpeningBook.add("e2e4 e7e5 g1f3", "Open Game Test");

            Move bookMove = OpeningBook.choose(Board.starting());

            assertNotNull(bookMove);
            assertEquals("e2e4", bookMove.coordinate());
        }

        @Test
        @DisplayName("Opening book follows already played history")
        void openingBookFollowsHistory() {
            OpeningBook.add("e2e4 e7e5 g1f3", "Open Game Test");
            Board board = Board.starting();

            play(board, "e2e4");

            Move bookMove = OpeningBook.choose(board);
            assertNotNull(bookMove);
            assertEquals("e7e5", bookMove.coordinate());
        }

        @Test
        @DisplayName("Opening book returns null if the book move is illegal")
        void openingBookReturnsNullForIllegalBookMove() {
            OpeningBook.add("a1a8", "Impossible Start Move");

            assertNull(OpeningBook.choose(Board.starting()));
        }

        @Test
        @DisplayName("currentName reports out of book at start and opening name after a matching move")
        void currentNameReportsBookName() {
            OpeningBook.add("e2e4 e7e5 g1f3", "Open Game Test");
            Board board = Board.starting();

            assertEquals("Out of book", OpeningBook.currentName(board));

            play(board, "e2e4");

            assertEquals("Open Game Test", OpeningBook.currentName(board));
        }

        @Test
        @DisplayName("historyCoordinates returns move coordinates in order")
        void historyCoordinatesReturnsCoordinatesInOrder() {
            Board board = Board.starting();

            play(board, "e2e4");
            play(board, "e7e5");
            play(board, "g1f3");

            assertEquals(List.of("e2e4", "e7e5", "g1f3"), OpeningBook.historyCoordinates(board));
        }

        @Test
        @DisplayName("fromCoordinate returns null for invalid coordinate text")
        void fromCoordinateRejectsInvalidText() {
            Board board = Board.starting();

            assertNull(OpeningBook.fromCoordinate(board, null));
            assertNull(OpeningBook.fromCoordinate(board, "bad"));
            assertNull(OpeningBook.fromCoordinate(board, "i9e4"));
        }

        @Test
        @DisplayName("ensureOpeningBookFileExists creates a default txt file")
        void ensureOpeningBookFileExistsCreatesFile(@TempDir Path tempDir) throws IOException {
            Path file = tempDir.resolve("chesspro_openings.txt");

            OpeningBook.ensureOpeningBookFileExists(file);

            assertTrue(Files.exists(file));
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(content.contains("ChessPro opening book"));
            assertTrue(content.contains("e2e4"));
        }
    }

    @Nested
    @DisplayName("Chess AI and evaluation")
    class ChessAiTests {

        @Test
        @DisplayName("Beginner random AI returns a legal move and does not mutate the board")
        void beginnerAiReturnsLegalMoveWithoutMutatingBoard() {
            Board board = Board.starting();
            String before = board.toFen();

            Move move = ChessAI.chooseMove(board, AiLevel.BEGINNER_RANDOM);

            assertNotNull(move);
            assertTrue(hasLegalMove(board, move.coordinate()));
            assertEquals(before, board.toFen());
        }

        @Test
        @DisplayName("Depth-one engine analysis returns a legal best move")
        @Timeout(10)
        void depthOneAnalysisReturnsLegalBestMove() {
            Board board = Board.starting();

            ChessAI.SearchResult result = assertTimeout(Duration.ofSeconds(10),
                    () -> ChessAI.analyze(board, AiLevel.TRAINING_400));

            assertNotNull(result.bestMove);
            assertTrue(hasLegalMove(board, result.bestMove.coordinate()));
            assertTrue(result.searchedDepth >= 1);
            assertTrue(result.nodes > 0);
            assertFalse(result.candidates.isEmpty());
        }

        @Test
        @DisplayName("AI returns no move in a checkmated position")
        void aiReturnsNoMoveInCheckmatePosition() {
            Board board = Board.starting();
            play(board, "f2f3");
            play(board, "e7e5");
            play(board, "g2g4");
            play(board, "d8h4");

            assertNull(ChessAI.chooseMove(board, AiLevel.TRAINING_400));
        }

        @Test
        @DisplayName("Evaluation is positive when White has an extra queen")
        void evaluationPositiveForWhiteMaterialAdvantage() {
            Board board = Board.fromFen("rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

            assertTrue(ChessAI.evaluate(board) > 700);
        }

        @Test
        @DisplayName("Evaluation is negative when Black has an extra queen")
        void evaluationNegativeForBlackMaterialAdvantage() {
            Board board = Board.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR b KQkq - 0 1");

            assertTrue(ChessAI.evaluate(board) < -700);
        }

        @Test
        @DisplayName("Formatted evaluation uses centipawn style")
        void formatEvalWorks() {
            assertEquals("+1.23", ChessAI.formatEval(123));
            assertEquals("-0.45", ChessAI.formatEval(-45));
        }

        @Test
        @DisplayName("Move ordering keeps all moves and produces a legal first move")
        void moveOrderingKeepsMoves() {
            Board board = Board.starting();
            List<Move> moves = new ArrayList<>(board.legalMoves());
            int beforeSize = moves.size();

            ChessAI.orderMoves(board, moves);

            assertEquals(beforeSize, moves.size());
            assertTrue(hasLegalMove(board, moves.get(0).coordinate()));
        }
    }

    @Nested
    @DisplayName("Game review")
    class GameReviewTests {

        @Test
        @DisplayName("Move classification thresholds work")
        void classifyThresholdsWork() {
            Board board = Board.starting();
            Move move = legalMove(board, "e2e4");

            assertEquals("Best", GameReview.classify(0, false, move, board));
            assertEquals("Excellent", GameReview.classify(30, false, move, board));
            assertEquals("Good", GameReview.classify(80, false, move, board));
            assertEquals("Inaccuracy", GameReview.classify(120, false, move, board));
            assertEquals("Mistake", GameReview.classify(250, false, move, board));
            assertEquals("Blunder", GameReview.classify(400, false, move, board));
        }

        @Test
        @DisplayName("Review output contains header and summary")
        @Timeout(15)
        void reviewOutputContainsHeaderAndSummary() {
            Board board = Board.starting();
            play(board, "e2e4");
            play(board, "e7e5");

            String review = assertTimeout(Duration.ofSeconds(15),
                    () -> GameReview.review(board, Board.starting().toFen(), AiLevel.TRAINING_400));

            assertTrue(review.contains("Game review"));
            assertTrue(review.contains("Summary"));
            assertTrue(review.contains("White:"));
            assertTrue(review.contains("Black:"));
        }

        @Test
        @DisplayName("findEquivalentLegalMove finds the matching legal move")
        void findEquivalentLegalMoveWorks() {
            Board board = Board.starting();
            Move original = new Move(sq("e2"), sq("e4"));

            Move equivalent = GameReview.findEquivalentLegalMove(board, original);

            assertNotNull(equivalent);
            assertEquals("e2e4", equivalent.coordinate());
        }
    }

    @Nested
    @DisplayName("Puzzle book, themes and UI helpers")
    class UtilityTests {

        @Test
        @DisplayName("Puzzle book contains usable puzzles")
        void puzzleBookContainsUsablePuzzles() {
            assertTrue(PuzzleBook.PUZZLES.length >= 5);

            for (TrainingPuzzle puzzle : PuzzleBook.PUZZLES) {
                assertNotNull(puzzle.title);
                assertNotNull(puzzle.fen);
                assertNotNull(puzzle.solution);
                assertNotNull(puzzle.explanation);
                assertDoesNotThrow(() -> Board.fromFen(puzzle.fen));
            }
        }

        @Test
        @DisplayName("Random puzzle returns one of the stored puzzles")
        void randomPuzzleReturnsPuzzle() {
            TrainingPuzzle puzzle = PuzzleBook.randomPuzzle();

            assertNotNull(puzzle);
            assertNotNull(puzzle.title);
            assertNotNull(puzzle.solution);
        }

        @Test
        @DisplayName("Themes have unique names")
        void themesHaveUniqueNames() {
            Theme[] themes = Theme.all();
            Set<String> names = new HashSet<>();

            assertTrue(themes.length >= 5);
            for (Theme theme : themes) {
                assertNotNull(theme.name);
                assertTrue(names.add(theme.name), "Duplicate theme name: " + theme.name);
                assertNotNull(theme.light);
                assertNotNull(theme.dark);
                assertNotNull(theme.bg);
                assertNotNull(theme.fg);
            }
        }

        @Test
        @DisplayName("UiColors.blend blends between two colors")
        void blendWorks() {
            Color black = new Color(0, 0, 0);
            Color white = new Color(255, 255, 255);

            Color middle = UiColors.blend(black, white, 0.5);

            assertEquals(128, middle.getRed(), 1);
            assertEquals(128, middle.getGreen(), 1);
            assertEquals(128, middle.getBlue(), 1);
        }

        @Test
        @DisplayName("UiColors.withAlpha clamps alpha into valid range")
        void withAlphaClampsAlpha() {
            Color c = new Color(10, 20, 30);

            assertEquals(0, UiColors.withAlpha(c, -50).getAlpha());
            assertEquals(255, UiColors.withAlpha(c, 300).getAlpha());
            assertEquals(123, UiColors.withAlpha(c, 123).getAlpha());
        }
    }

    @Nested
    @DisplayName("Elo store")
    class EloStoreTests {

        @Test
        @DisplayName("EloStore starts at 1200, can update and can reset")
        void eloStoreStartsUpdatesAndResets(@TempDir Path tempDir) {
            String oldHome = System.getProperty("user.home");
            try {
                System.setProperty("user.home", tempDir.toString());

                EloStore store = new EloStore();

                assertEquals(1200, store.get("human"));
                assertEquals(1200, store.get("ai"));
                assertTrue(store.label().contains("Human Elo: 1200"));

                store.update(1.0);
                assertTrue(store.get("human") > 1200);
                assertTrue(store.get("ai") < 1200);

                store.reset();
                assertEquals(1200, store.get("human"));
                assertEquals(1200, store.get("ai"));
                assertTrue(Files.exists(tempDir.resolve("chesspro_elo.properties")));
            } finally {
                System.setProperty("user.home", oldHome);
            }
        }
    }
}

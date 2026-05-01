import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * ChessPro.java - Ultimate Coach Edition
 *
 * A compact but complete playable Java chess program:
 * - Legal chess move generation: check, checkmate, stalemate, castling, en passant, promotion
 * - Swing GUI with themes, board flip, coordinate display, last-move and legal-move highlights
 * - Modes: Human vs Human, Human vs AI, AI vs Human, AI vs AI
 * - AI levels: Random, Greedy, Minimax depth 2/3/4 with basic move ordering
 * - Undo/Redo, hint move, FEN copy/load, PGN-like export
 * - Game review: evaluates every position after each move and marks blunders/inaccuracies
 * - Simple local Elo system saved in chesspro_elo.properties
 *
 * Compile: javac ChessPro.java
 * Run:     java ChessPro
 */
public class ChessPro {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {
            // Fall back to the platform look and feel.
        }
        SwingUtilities.invokeLater(() -> new ChessFrame().setVisible(true));
    }
}

enum Side {
    WHITE(1), BLACK(-1);
    final int sign;
    Side(int s) { sign = s; }
    Side opposite() { return this == WHITE ? BLACK : WHITE; }
    public String toString() { return this == WHITE ? "White" : "Black"; }
}

enum AiLevel {
    BEGINNER_RANDOM("Beginner Random", 0, 100, false, 25_000),
    TRAINING_400("Training 400", 1, 88, false, 35_000),
    KIDS_500("Kids 500", 1, 78, false, 45_000),
    BEGINNER_BLUNDER("Beginner 650", 1, 65, false, 70_000),
    NOVICE("Novice 850", 1, 42, true, 100_000),
    GREEDY("Greedy 950", 1, 24, true, 150_000),
    CASUAL("Casual 1150", 2, 18, true, 260_000),
    SOLID("Solid 1300", 2, 10, true, 380_000),
    CLUB("Club 1450", 3, 7, true, 620_000),
    SHARP_TACTICIAN("Sharp Tactician 1650", 4, 5, true, 950_000),
    POSITIONAL("Positional 1850", 4, 3, true, 1_200_000),
    ENDGAME_SPECIALIST("Endgame Specialist 1950", 5, 2, true, 1_450_000),
    EXPERT("Expert 2050", 5, 2, true, 1_850_000),
    MASTER("Master 2250", 6, 1, true, 2_750_000),
    CHAMPION("Champion 2450", 7, 0, true, 3_700_000),
    GRANDMASTER("Grandmaster 2600", 8, 0, true, 4_500_000),
    ANALYSIS_FAST("Analysis fast depth 6", 6, 0, true, 2_500_000),
    ANALYSIS("Analysis depth 8", 8, 0, true, 5_200_000);

    final String label;
    final int depth;
    final int randomnessPercent;
    final boolean quiescence;
    final long nodeLimit;

    AiLevel(String label, int depth, int randomnessPercent, boolean quiescence, long nodeLimit) {
        this.label = label;
        this.depth = depth;
        this.randomnessPercent = randomnessPercent;
        this.quiescence = quiescence;
        this.nodeLimit = nodeLimit;
    }

    boolean isEngineSearch() { return depth > 0; }
    public String toString() { return label; }
}
enum GameMode {
    HUMAN_VS_HUMAN("Human vs Human"),
    HUMAN_VS_AI("Human vs AI"),
    AI_VS_HUMAN("AI vs Human"),
    AI_VS_AI("AI vs AI");

    final String label;
    GameMode(String l) { label = l; }
    public String toString() { return label; }
}

class Piece {
    static final int EMPTY = 0;
    static final int PAWN = 1;
    static final int KNIGHT = 2;
    static final int BISHOP = 3;
    static final int ROOK = 4;
    static final int QUEEN = 5;
    static final int KING = 6;

    static boolean isWhite(int p) { return p > 0; }
    static boolean isBlack(int p) { return p < 0; }
    static int type(int p) { return Math.abs(p); }
    static Side side(int p) { return p > 0 ? Side.WHITE : Side.BLACK; }

    static String unicode(int p) {
        switch (p) {
            case 1: return "♙";
            case 2: return "♘";
            case 3: return "♗";
            case 4: return "♖";
            case 5: return "♕";
            case 6: return "♔";
            case -1: return "♟";
            case -2: return "♞";
            case -3: return "♝";
            case -4: return "♜";
            case -5: return "♛";
            case -6: return "♚";
            default: return "";
        }
    }

    static char fenChar(int p) {
        char c;
        switch (type(p)) {
            case 1: c = 'p'; break;
            case 2: c = 'n'; break;
            case 3: c = 'b'; break;
            case 4: c = 'r'; break;
            case 5: c = 'q'; break;
            case 6: c = 'k'; break;
            default: c = '1'; break;
        }
        return p > 0 ? Character.toUpperCase(c) : c;
    }

    static int fromFenChar(char c) {
        int sign = Character.isUpperCase(c) ? 1 : -1;
        switch (Character.toLowerCase(c)) {
            case 'p': return sign * PAWN;
            case 'n': return sign * KNIGHT;
            case 'b': return sign * BISHOP;
            case 'r': return sign * ROOK;
            case 'q': return sign * QUEEN;
            case 'k': return sign * KING;
            default: return 0;
        }
    }

    static String letter(int p) {
        switch(type(p)) {
            case 2: return "N";
            case 3: return "B";
            case 4: return "R";
            case 5: return "Q";
            case 6: return "K";
            default: return "";
        }
    }

    static int value(int type) {
        switch(type) {
            case 1: return 100;
            case 2: return 320;
            case 3: return 330;
            case 4: return 500;
            case 5: return 900;
            case 6: return 20000;
            default: return 0;
        }
    }

    static String name(int p) {
        switch (type(p)) {
            case PAWN: return "Pawn";
            case KNIGHT: return "Knight";
            case BISHOP: return "Bishop";
            case ROOK: return "Rook";
            case QUEEN: return "Queen";
            case KING: return "King";
            default: return "";
        }
    }
}

class Move {
    final int from, to;
    final int promotion;
    boolean castleKingSide;
    boolean castleQueenSide;
    boolean enPassant;
    int movedPiece;
    int capturedPiece;
    String notation = "";

    Move(int from, int to) { this(from, to, 0); }
    Move(int from, int to, int promotion) {
        this.from = from;
        this.to = to;
        this.promotion = promotion;
    }

    public String coordinate() {
        return Board.squareName(from) + Board.squareName(to) + (promotion != 0 ? Character.toLowerCase(Piece.fenChar(promotion)) : "");
    }

    public String toString() {
        return notation == null || notation.isEmpty() ? coordinate() : notation;
    }

    boolean sameUserMove(Move other) {
        if (other == null) return false;
        return from == other.from && to == other.to && promotion == other.promotion;
    }
}

class Board {
    int[] sq = new int[64];
    Side turn = Side.WHITE;

    boolean whiteKingCastle = true;
    boolean whiteQueenCastle = true;
    boolean blackKingCastle = true;
    boolean blackQueenCastle = true;
    int epSquare = -1;
    int halfmoveClock = 0;
    int fullmoveNumber = 1;

    final List<Move> history = new ArrayList<>();
    final List<String> fenHistory = new ArrayList<>();

    static int idx(int row, int col) { return row * 8 + col; }
    static int row(int i) { return i / 8; }
    static int col(int i) { return i % 8; }
    static boolean on(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }

    static String squareName(int i) {
        return "" + (char)('a' + col(i)) + (char)('8' - row(i));
    }

    static int squareFromName(String s) {
        if (s == null || s.length() != 2) return -1;
        int c = s.charAt(0) - 'a';
        int r = '8' - s.charAt(1);
        if (!on(r, c)) return -1;
        return idx(r, c);
    }

    static Board starting() {
        Board b = new Board();
        int[] back = {Piece.ROOK, Piece.KNIGHT, Piece.BISHOP, Piece.QUEEN, Piece.KING, Piece.BISHOP, Piece.KNIGHT, Piece.ROOK};
        for (int c = 0; c < 8; c++) {
            b.sq[idx(0, c)] = -back[c];
            b.sq[idx(1, c)] = -Piece.PAWN;
            b.sq[idx(6, c)] = Piece.PAWN;
            b.sq[idx(7, c)] = back[c];
        }
        b.fenHistory.add(b.toFen());
        return b;
    }

    static Board fromFen(String fen) {
        if (fen == null) throw new IllegalArgumentException("FEN is empty.");
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("A FEN needs at least board, turn, castling and en-passant fields.");
        }

        Board b = new Board();
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("The board part of the FEN must contain 8 ranks.");

        for (int r = 0; r < 8; r++) {
            int c = 0;
            for (int k = 0; k < ranks[r].length(); k++) {
                char ch = ranks[r].charAt(k);
                if (Character.isDigit(ch)) {
                    c += ch - '0';
                } else {
                    if (c >= 8) throw new IllegalArgumentException("Too many squares in rank " + (8 - r) + ".");
                    int p = Piece.fromFenChar(ch);
                    if (p == 0) throw new IllegalArgumentException("Unknown piece in FEN: " + ch);
                    b.sq[idx(r, c)] = p;
                    c++;
                }
            }
            if (c != 8) throw new IllegalArgumentException("Rank " + (8 - r) + " does not contain 8 squares.");
        }

        b.turn = parts[1].equals("b") ? Side.BLACK : Side.WHITE;
        b.whiteKingCastle = parts[2].contains("K");
        b.whiteQueenCastle = parts[2].contains("Q");
        b.blackKingCastle = parts[2].contains("k");
        b.blackQueenCastle = parts[2].contains("q");
        b.epSquare = parts[3].equals("-") ? -1 : squareFromName(parts[3]);
        if (!parts[3].equals("-") && b.epSquare == -1) throw new IllegalArgumentException("Invalid en-passant square.");
        if (parts.length >= 5) b.halfmoveClock = Math.max(0, Integer.parseInt(parts[4]));
        if (parts.length >= 6) b.fullmoveNumber = Math.max(1, Integer.parseInt(parts[5]));

        b.fenHistory.add(b.toFen());
        return b;
    }

    Board copy() {
        Board b = new Board();
        b.sq = sq.clone();
        b.turn = turn;
        b.whiteKingCastle = whiteKingCastle;
        b.whiteQueenCastle = whiteQueenCastle;
        b.blackKingCastle = blackKingCastle;
        b.blackQueenCastle = blackQueenCastle;
        b.epSquare = epSquare;
        b.halfmoveClock = halfmoveClock;
        b.fullmoveNumber = fullmoveNumber;
        return b;
    }

    String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                int p = sq[idx(r, c)];
                if (p == 0) empty++;
                else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(Piece.fenChar(p));
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < 7) sb.append("/");
        }
        sb.append(turn == Side.WHITE ? " w " : " b ");
        String castling = "";
        if (whiteKingCastle) castling += "K";
        if (whiteQueenCastle) castling += "Q";
        if (blackKingCastle) castling += "k";
        if (blackQueenCastle) castling += "q";
        sb.append(castling.isEmpty() ? "-" : castling).append(" ");
        sb.append(epSquare == -1 ? "-" : squareName(epSquare)).append(" ");
        sb.append(halfmoveClock).append(" ").append(fullmoveNumber);
        return sb.toString();
    }

    String materialLabel() {
        int white = 0, black = 0;
        Map<Integer, Integer> count = new TreeMap<>();
        for (int p : sq) {
            if (p == 0 || Piece.type(p) == Piece.KING) continue;
            int v = Piece.value(Piece.type(p));
            if (p > 0) white += v;
            else black += v;
            count.put(p, count.getOrDefault(p, 0) + 1);
        }
        int diff = (white - black) / 100;
        if (diff == 0) return "Material: equal";
        return "Material: " + (diff > 0 ? "White +" + diff : "Black +" + (-diff));
    }

    List<Move> legalMoves() {
        List<Move> moves = pseudoMoves(turn, true);
        List<Move> legal = new ArrayList<>();
        for (Move m : moves) {
            Board b = copy();
            b.makeMoveNoHistory(m);
            if (!b.isInCheck(turn)) {
                m.notation = simpleNotation(m);
                legal.add(m);
            }
        }
        return legal;
    }

    Move findMatchingLegal(Move userMove) {
        for (Move legal : legalMoves()) {
            if (legal.sameUserMove(userMove)) return legal;
        }
        return null;
    }

    String simpleNotation(Move m) {
        int p = sq[m.from];
        if (m.castleKingSide) return "O-O";
        if (m.castleQueenSide) return "O-O-O";
        String cap = (m.enPassant || sq[m.to] != 0) ? "x" : "";
        String promo = m.promotion != 0 ? "=" + Piece.letter(m.promotion) : "";
        if (Piece.type(p) == Piece.PAWN) {
            String pawnFile = cap.isEmpty() ? "" : String.valueOf((char)('a' + col(m.from)));
            return pawnFile + cap + squareName(m.to) + promo;
        }
        return Piece.letter(p) + cap + squareName(m.to) + promo;
    }

    boolean makeUserMove(Move userMove) {
        Move legal = findMatchingLegal(userMove);
        if (legal != null) {
            makeMove(legal);
            return true;
        }
        return false;
    }

    void makeMove(Move m) {
        makeMoveNoHistory(m);
        history.add(m);
        fenHistory.add(toFen());
    }

    void makeMoveNoHistory(Move m) {
        int piece = sq[m.from];
        int target = sq[m.to];
        m.movedPiece = piece;
        m.capturedPiece = target;

        boolean pawnMove = Piece.type(piece) == Piece.PAWN;
        boolean capture = target != 0 || m.enPassant;

        sq[m.from] = 0;

        if (m.enPassant) {
            int capSq = turn == Side.WHITE ? m.to + 8 : m.to - 8;
            m.capturedPiece = sq[capSq];
            sq[capSq] = 0;
        }

        int placed = piece;
        if (m.promotion != 0) {
            placed = turn.sign * Piece.type(m.promotion);
        }
        sq[m.to] = placed;

        if (Piece.type(piece) == Piece.KING) {
            if (turn == Side.WHITE) {
                whiteKingCastle = false;
                whiteQueenCastle = false;
            } else {
                blackKingCastle = false;
                blackQueenCastle = false;
            }
            if (m.castleKingSide) {
                if (turn == Side.WHITE) {
                    sq[idx(7, 5)] = sq[idx(7, 7)];
                    sq[idx(7, 7)] = 0;
                } else {
                    sq[idx(0, 5)] = sq[idx(0, 7)];
                    sq[idx(0, 7)] = 0;
                }
            } else if (m.castleQueenSide) {
                if (turn == Side.WHITE) {
                    sq[idx(7, 3)] = sq[idx(7, 0)];
                    sq[idx(7, 0)] = 0;
                } else {
                    sq[idx(0, 3)] = sq[idx(0, 0)];
                    sq[idx(0, 0)] = 0;
                }
            }
        }

        updateCastlingRights(m.from, m.to, piece, target);

        epSquare = -1;
        if (pawnMove && Math.abs(row(m.from) - row(m.to)) == 2) {
            epSquare = (m.from + m.to) / 2;
        }

        if (pawnMove || capture) halfmoveClock = 0;
        else halfmoveClock++;

        if (turn == Side.BLACK) fullmoveNumber++;
        turn = turn.opposite();
    }

    void updateCastlingRights(int from, int to, int piece, int target) {
        if (from == idx(7, 0) || to == idx(7, 0)) whiteQueenCastle = false;
        if (from == idx(7, 7) || to == idx(7, 7)) whiteKingCastle = false;
        if (from == idx(0, 0) || to == idx(0, 0)) blackQueenCastle = false;
        if (from == idx(0, 7) || to == idx(0, 7)) blackKingCastle = false;

        if (Piece.type(piece) == Piece.KING) {
            if (piece > 0) { whiteKingCastle = false; whiteQueenCastle = false; }
            else { blackKingCastle = false; blackQueenCastle = false; }
        }
        if (target != 0 && Piece.type(target) == Piece.ROOK) {
            if (to == idx(7, 0)) whiteQueenCastle = false;
            if (to == idx(7, 7)) whiteKingCastle = false;
            if (to == idx(0, 0)) blackQueenCastle = false;
            if (to == idx(0, 7)) blackKingCastle = false;
        }
    }

    boolean isGameOver() {
        return legalMoves().isEmpty() || halfmoveClock >= 100 || insufficientMaterial();
    }

    boolean insufficientMaterial() {
        int bishops = 0, knights = 0, others = 0;
        for (int p : sq) {
            int t = Piece.type(p);
            if (t == Piece.EMPTY || t == Piece.KING) continue;
            if (t == Piece.BISHOP) bishops++;
            else if (t == Piece.KNIGHT) knights++;
            else others++;
        }
        if (others > 0) return false;
        return bishops + knights <= 1;
    }

    String resultString() {
        List<Move> moves = legalMoves();
        if (halfmoveClock >= 100) return "1/2-1/2 by fifty-move rule";
        if (insufficientMaterial()) return "1/2-1/2 by insufficient material";
        if (!moves.isEmpty()) return "*";
        if (isInCheck(turn)) return turn == Side.WHITE ? "0-1 checkmate" : "1-0 checkmate";
        return "1/2-1/2 stalemate";
    }

    double scoreForWhite() {
        String r = resultString();
        if (r.startsWith("1-0")) return 1.0;
        if (r.startsWith("0-1")) return 0.0;
        if (r.startsWith("1/2")) return 0.5;
        return 0.5;
    }

    boolean isInCheck(Side side) {
        int king = side.sign * Piece.KING;
        int kingSq = -1;
        for (int i = 0; i < 64; i++) if (sq[i] == king) { kingSq = i; break; }
        if (kingSq == -1) return true;
        return isAttacked(kingSq, side.opposite());
    }

    boolean isAttacked(int square, Side bySide) {
        int r = row(square), c = col(square);

        int pawnDir = bySide == Side.WHITE ? -1 : 1;
        int pr = r - pawnDir;
        for (int dc : new int[]{-1, 1}) {
            int pc = c + dc;
            if (on(pr, pc) && sq[idx(pr, pc)] == bySide.sign * Piece.PAWN) return true;
        }

        int[][] knightD = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : knightD) {
            int nr = r + d[0], nc = c + d[1];
            if (on(nr, nc) && sq[idx(nr, nc)] == bySide.sign * Piece.KNIGHT) return true;
        }

        int[][] bishopD = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] d : bishopD) {
            if (rayAttacks(r, c, d[0], d[1], bySide, Piece.BISHOP, Piece.QUEEN)) return true;
        }

        int[][] rookD = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int[] d : rookD) {
            if (rayAttacks(r, c, d[0], d[1], bySide, Piece.ROOK, Piece.QUEEN)) return true;
        }

        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
            if (dr == 0 && dc == 0) continue;
            int nr = r + dr, nc = c + dc;
            if (on(nr, nc) && sq[idx(nr, nc)] == bySide.sign * Piece.KING) return true;
        }

        return false;
    }

    boolean rayAttacks(int r, int c, int dr, int dc, Side bySide, int t1, int t2) {
        r += dr; c += dc;
        while (on(r, c)) {
            int p = sq[idx(r, c)];
            if (p != 0) {
                return Integer.signum(p) == bySide.sign && (Piece.type(p) == t1 || Piece.type(p) == t2);
            }
            r += dr; c += dc;
        }
        return false;
    }

    List<Move> pseudoMoves(Side side, boolean includeCastles) {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int p = sq[i];
            if (p == 0 || Integer.signum(p) != side.sign) continue;
            switch (Piece.type(p)) {
                case 1:
                    pawnMoves(i, side, moves);
                    break;
                case 2:
                    jumpMoves(i, side, moves, new int[][]{{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}});
                    break;
                case 3:
                    slideMoves(i, side, moves, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}});
                    break;
                case 4:
                    slideMoves(i, side, moves, new int[][]{{-1,0},{1,0},{0,-1},{0,1}});
                    break;
                case 5:
                    slideMoves(i, side, moves, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1},{-1,0},{1,0},{0,-1},{0,1}});
                    break;
                case 6:
                    kingMoves(i, side, moves, includeCastles);
                    break;
            }
        }
        return moves;
    }

    void pawnMoves(int i, Side side, List<Move> moves) {
        int r = row(i), c = col(i);
        int dir = side == Side.WHITE ? -1 : 1;
        int startRow = side == Side.WHITE ? 6 : 1;
        int promoteRow = side == Side.WHITE ? 0 : 7;

        int oneR = r + dir;
        if (on(oneR, c) && sq[idx(oneR, c)] == 0) {
            addPawnMove(i, idx(oneR, c), side, promoteRow, moves);
            int twoR = r + 2 * dir;
            if (r == startRow && on(twoR, c) && sq[idx(twoR, c)] == 0) {
                moves.add(new Move(i, idx(twoR, c)));
            }
        }

        for (int dc : new int[]{-1, 1}) {
            int nr = r + dir, nc = c + dc;
            if (!on(nr, nc)) continue;
            int to = idx(nr, nc);
            if (sq[to] != 0 && Integer.signum(sq[to]) == -side.sign) {
                addPawnMove(i, to, side, promoteRow, moves);
            }
            if (to == epSquare) {
                Move m = new Move(i, to);
                m.enPassant = true;
                moves.add(m);
            }
        }
    }

    void addPawnMove(int from, int to, Side side, int promoteRow, List<Move> moves) {
        if (row(to) == promoteRow) {
            moves.add(new Move(from, to, side.sign * Piece.QUEEN));
            moves.add(new Move(from, to, side.sign * Piece.ROOK));
            moves.add(new Move(from, to, side.sign * Piece.BISHOP));
            moves.add(new Move(from, to, side.sign * Piece.KNIGHT));
        } else {
            moves.add(new Move(from, to));
        }
    }

    void jumpMoves(int i, Side side, List<Move> moves, int[][] deltas) {
        int r = row(i), c = col(i);
        for (int[] d : deltas) {
            int nr = r + d[0], nc = c + d[1];
            if (!on(nr, nc)) continue;
            int p = sq[idx(nr, nc)];
            if (p == 0 || Integer.signum(p) == -side.sign) moves.add(new Move(i, idx(nr, nc)));
        }
    }

    void slideMoves(int i, Side side, List<Move> moves, int[][] dirs) {
        int r = row(i), c = col(i);
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (on(nr, nc)) {
                int p = sq[idx(nr, nc)];
                if (p == 0) moves.add(new Move(i, idx(nr, nc)));
                else {
                    if (Integer.signum(p) == -side.sign) moves.add(new Move(i, idx(nr, nc)));
                    break;
                }
                nr += d[0]; nc += d[1];
            }
        }
    }

    void kingMoves(int i, Side side, List<Move> moves, boolean includeCastles) {
        jumpMoves(i, side, moves, new int[][]{{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}});
        if (!includeCastles || isInCheck(side)) return;

        if (side == Side.WHITE) {
            if (whiteKingCastle && sq[idx(7,5)] == 0 && sq[idx(7,6)] == 0 &&
                    !isAttacked(idx(7,5), Side.BLACK) && !isAttacked(idx(7,6), Side.BLACK)) {
                Move m = new Move(i, idx(7,6)); m.castleKingSide = true; moves.add(m);
            }
            if (whiteQueenCastle && sq[idx(7,1)] == 0 && sq[idx(7,2)] == 0 && sq[idx(7,3)] == 0 &&
                    !isAttacked(idx(7,2), Side.BLACK) && !isAttacked(idx(7,3), Side.BLACK)) {
                Move m = new Move(i, idx(7,2)); m.castleQueenSide = true; moves.add(m);
            }
        } else {
            if (blackKingCastle && sq[idx(0,5)] == 0 && sq[idx(0,6)] == 0 &&
                    !isAttacked(idx(0,5), Side.WHITE) && !isAttacked(idx(0,6), Side.WHITE)) {
                Move m = new Move(i, idx(0,6)); m.castleKingSide = true; moves.add(m);
            }
            if (blackQueenCastle && sq[idx(0,1)] == 0 && sq[idx(0,2)] == 0 && sq[idx(0,3)] == 0 &&
                    !isAttacked(idx(0,2), Side.WHITE) && !isAttacked(idx(0,3), Side.WHITE)) {
                Move m = new Move(i, idx(0,2)); m.castleQueenSide = true; moves.add(m);
            }
        }
    }
}

class ChessAI {
    static final Random RNG = new Random();
    static final int INF = 1_000_000;
    static final int MATE = 900_000;
    static final int MAX_Q_DEPTH = 8;

    static long nodes;
    static long nodeLimit;
    static boolean useQuiescence;

    // Lightweight chess-engine heuristics.
    // Killer moves remember quiet moves that caused cutoffs at a given ply.
    // The history table remembers generally successful quiet moves for each side.
    static final Move[][] killerMoves = new Move[128][2];
    static final int[][] historyHeuristic = new int[2][4096];

    static final Map<String, TTEntry> TT = new HashMap<>();

    static final int TT_EXACT = 0;
    static final int TT_LOWER = 1;
    static final int TT_UPPER = 2;

    static class TTEntry {
        final int depth;
        final int value;
        final int flag;
        TTEntry(int depth, int value, int flag) {
            this.depth = depth;
            this.value = value;
            this.flag = flag;
        }
    }

    static class ScoredMove {
        final Move move;
        final int scoreSide;
        final int scoreWhite;
        ScoredMove(Move move, int scoreSide, Side rootSide) {
            this.move = move;
            this.scoreSide = scoreSide;
            this.scoreWhite = scoreSide * rootSide.sign;
        }
    }

    static class SearchResult {
        Move bestMove;
        int scoreWhite;
        int searchedDepth;
        long nodes;
        String principalVariation = "";
        List<ScoredMove> candidates = new ArrayList<>();

        String scoreLabel() { return formatEval(scoreWhite); }
    }

    static Move chooseMove(Board b, AiLevel level) {
        SearchResult r = analyze(b, level);
        return r.bestMove;
    }

    static SearchResult analyze(Board b, AiLevel level) {
        SearchResult result = new SearchResult();
        List<Move> moves = b.legalMoves();
        if (moves.isEmpty()) {
            result.scoreWhite = evaluateTerminalAwareWhite(b, moves, 0);
            return result;
        }

        if (level == AiLevel.BEGINNER_RANDOM) {
            Move m = moves.get(RNG.nextInt(moves.size()));
            Board next = b.copy();
            next.makeMoveNoHistory(m);
            result.bestMove = m;
            result.scoreWhite = evaluate(next);
            result.searchedDepth = 0;
            result.principalVariation = m.toString();
            return result;
        }

        nodes = 0;
        nodeLimit = Math.max(20_000, level.nodeLimit);
        useQuiescence = level.quiescence;
        clearKillerMoves();
        if (TT.size() > 300_000) TT.clear();

        int targetDepth = Math.max(1, level.depth);
        Side rootSide = b.turn;
        orderMoves(b, moves, 0);

        List<ScoredMove> scored = new ArrayList<>();
        Move best = moves.get(0);
        int bestScore = -INF;
        int completedDepth = 0;

        // Iterative deepening improves practical strength:
        // shallow searches improve move ordering for deeper searches and still return a good move if the node limit is reached.
        for (int currentDepth = 1; currentDepth <= targetDepth; currentDepth++) {
            if (nodes > nodeLimit) break;

            List<Move> rootMoves = new ArrayList<>(moves);
            orderRootMoves(b, rootMoves, best);

            List<ScoredMove> currentScored = new ArrayList<>();
            Move currentBest = rootMoves.get(0);
            int currentBestScore = -INF;
            int alpha = -INF;
            int beta = INF;

            for (Move m : rootMoves) {
                Board next = b.copy();
                next.makeMoveNoHistory(m);
                int score = -negamax(next, currentDepth - 1, -beta, -alpha, 1);
                currentScored.add(new ScoredMove(m, score, rootSide));

                if (score > currentBestScore) {
                    currentBestScore = score;
                    currentBest = m;
                }
                alpha = Math.max(alpha, currentBestScore);
                if (nodes > nodeLimit && currentScored.size() >= Math.max(1, rootMoves.size() / 3)) break;
            }

            if (!currentScored.isEmpty()) {
                currentScored.sort((a, c) -> Integer.compare(c.scoreSide, a.scoreSide));
                scored = currentScored;
                best = currentBest;
                bestScore = currentBestScore;
                completedDepth = currentDepth;
            }
        }

        if (scored.isEmpty()) {
            Move fallback = greedy(b, moves);
            Board next = b.copy();
            next.makeMoveNoHistory(fallback);
            best = fallback;
            bestScore = evaluate(next) * rootSide.sign;
            scored.add(new ScoredMove(best, bestScore, rootSide));
            completedDepth = 1;
        }

        Move selected = best;
        if (level.randomnessPercent > 0 && RNG.nextInt(100) < level.randomnessPercent && scored.size() > 1) {
            int pool;
            if (level.randomnessPercent >= 65) pool = Math.min(scored.size(), 8);
            else if (level.randomnessPercent >= 25) pool = Math.min(scored.size(), 5);
            else pool = Math.min(scored.size(), 3);
            selected = scored.get(RNG.nextInt(pool)).move;
        }

        int selectedScore = bestScore;
        for (ScoredMove sm : scored) {
            if (sm.move.sameUserMove(selected)) { selectedScore = sm.scoreSide; break; }
        }

        result.bestMove = selected;
        result.scoreWhite = selectedScore * rootSide.sign;
        result.searchedDepth = completedDepth;
        result.nodes = nodes;
        result.candidates = scored;
        result.principalVariation = principalVariation(b, selected, Math.min(Math.max(1, completedDepth), 6));
        return result;
    }

    static int negamax(Board b, int depth, int alpha, int beta, int ply) {
        nodes++;
        if (nodes > nodeLimit) return evaluateForSideToMove(b);

        List<Move> moves = b.legalMoves();
        if (moves.isEmpty() || b.halfmoveClock >= 100 || b.insufficientMaterial()) {
            return evaluateTerminalAwareSide(b, moves, ply);
        }

        if (depth <= 0) {
            if (useQuiescence) return quiescence(b, alpha, beta, ply, 0);
            return evaluateForSideToMove(b);
        }

        String key = ttKey(b, depth);
        int originalAlpha = alpha;
        int originalBeta = beta;
        TTEntry cached = TT.get(key);
        if (cached != null && cached.depth >= depth) {
            if (cached.flag == TT_EXACT) return cached.value;
            if (cached.flag == TT_LOWER) alpha = Math.max(alpha, cached.value);
            else if (cached.flag == TT_UPPER) beta = Math.min(beta, cached.value);
            if (alpha >= beta) return cached.value;
        }

        orderMoves(b, moves, ply);
        int best = -INF;
        for (Move m : moves) {
            Board next = b.copy();
            next.makeMoveNoHistory(m);
            int score = -negamax(next, depth - 1, -beta, -alpha, ply + 1);
            if (score > best) best = score;
            if (best > alpha) alpha = best;
            if (alpha >= beta) {
                if (isQuiet(b, m)) {
                    rememberKiller(m, ply);
                    rememberHistory(b.turn, m, depth);
                }
                break;
            }
        }
        int flag = TT_EXACT;
        if (best <= originalAlpha) flag = TT_UPPER;
        else if (best >= originalBeta) flag = TT_LOWER;
        TT.put(key, new TTEntry(depth, best, flag));
        return best;
    }

    static int quiescence(Board b, int alpha, int beta, int ply, int qDepth) {
        nodes++;
        if (nodes > nodeLimit || qDepth >= MAX_Q_DEPTH) return evaluateForSideToMove(b);

        int standPat = evaluateForSideToMove(b);
        if (standPat >= beta) return beta;
        if (alpha < standPat) alpha = standPat;

        List<Move> noisy = new ArrayList<>();
        for (Move m : b.legalMoves()) {
            if (b.sq[m.to] != 0 || m.enPassant || m.promotion != 0) noisy.add(m);
        }
        orderMoves(b, noisy);
        for (Move m : noisy) {
            Board next = b.copy();
            next.makeMoveNoHistory(m);
            int score = -quiescence(next, -beta, -alpha, ply + 1, qDepth + 1);
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    static String ttKey(Board b, int depth) {
        return depth + "|" + b.toFen();
    }

    static Move greedy(Board b, List<Move> moves) {
        Move best = moves.get(0);
        int bestScore = -INF;
        for (Move m : moves) {
            Board next = b.copy();
            next.makeMoveNoHistory(m);
            int s = evaluate(next) * b.turn.sign;
            if (s > bestScore) { bestScore = s; best = m; }
        }
        return best;
    }

    static void orderMoves(Board b, List<Move> moves) {
        orderMoves(b, moves, 0);
    }

    static void orderMoves(Board b, List<Move> moves, int ply) {
        moves.sort((a, c) -> Integer.compare(moveScore(b, c, ply), moveScore(b, a, ply)));
    }

    static void orderRootMoves(Board b, List<Move> moves, Move previousBest) {
        moves.sort((a, c) -> {
            int sa = moveScore(b, a, 0) + (previousBest != null && a.sameUserMove(previousBest) ? 50_000 : 0);
            int sc = moveScore(b, c, 0) + (previousBest != null && c.sameUserMove(previousBest) ? 50_000 : 0);
            return Integer.compare(sc, sa);
        });
    }

    static int moveScore(Board b, Move m) {
        return moveScore(b, m, 0);
    }

    static int moveScore(Board b, Move m, int ply) {
        int moving = b.sq[m.from];
        int target = b.sq[m.to];
        int score = 0;
        if (target != 0) score += 20_000 + 10 * Piece.value(Piece.type(target)) - Piece.value(Piece.type(moving));
        if (m.enPassant) score += 10_500;
        if (m.promotion != 0) score += 15_000 + Piece.value(Piece.type(m.promotion));
        if (m.castleKingSide || m.castleQueenSide) score += 600;

        if (ply >= 0 && ply < killerMoves.length) {
            if (killerMoves[ply][0] != null && killerMoves[ply][0].sameUserMove(m)) score += 9_000;
            if (killerMoves[ply][1] != null && killerMoves[ply][1].sameUserMove(m)) score += 8_000;
        }

        int sideIndex = b.turn == Side.WHITE ? 0 : 1;
        score += Math.min(6_000, historyHeuristic[sideIndex][moveCode(m)]);

        int toR = Board.row(m.to), toC = Board.col(m.to);
        int center = 14 - (Math.abs(3 - toR) + Math.abs(4 - toR) + Math.abs(3 - toC) + Math.abs(4 - toC));
        score += center * 6;

        Board next = b.copy();
        next.makeMoveNoHistory(m);
        if (next.isInCheck(next.turn)) score += 800;
        return score;
    }

    static int moveCode(Move m) {
        return (m.from << 6) | m.to;
    }

    static boolean isQuiet(Board b, Move m) {
        return b.sq[m.to] == 0 && !m.enPassant && m.promotion == 0;
    }

    static void clearKillerMoves() {
        for (int i = 0; i < killerMoves.length; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }
    }

    static void rememberKiller(Move m, int ply) {
        if (ply < 0 || ply >= killerMoves.length) return;
        if (killerMoves[ply][0] != null && killerMoves[ply][0].sameUserMove(m)) return;
        killerMoves[ply][1] = killerMoves[ply][0];
        killerMoves[ply][0] = new Move(m.from, m.to, m.promotion);
    }

    static void rememberHistory(Side side, Move m, int depth) {
        int sideIndex = side == Side.WHITE ? 0 : 1;
        int code = moveCode(m);
        historyHeuristic[sideIndex][code] += depth * depth;
        if (historyHeuristic[sideIndex][code] > 20_000) {
            for (int s = 0; s < historyHeuristic.length; s++) {
                for (int i = 0; i < historyHeuristic[s].length; i++) historyHeuristic[s][i] /= 2;
            }
        }
    }

    static int evaluateTerminalAwareSide(Board b, List<Move> legalMoves, int ply) {
        if (b.halfmoveClock >= 100 || b.insufficientMaterial()) return 0;
        if (legalMoves.isEmpty()) {
            if (b.isInCheck(b.turn)) return -MATE + ply;
            return 0;
        }
        return evaluateForSideToMove(b);
    }

    static int evaluateTerminalAwareWhite(Board b, List<Move> legalMoves, int ply) {
        if (b.halfmoveClock >= 100 || b.insufficientMaterial()) return 0;
        if (legalMoves.isEmpty()) {
            if (b.isInCheck(b.turn)) return b.turn == Side.WHITE ? -MATE + ply : MATE - ply;
            return 0;
        }
        return evaluate(b);
    }

    static int evaluateForSideToMove(Board b) {
        return evaluate(b) * b.turn.sign;
    }

    static int evaluate(Board b) {
        int score = 0;
        int whiteBishops = 0, blackBishops = 0;
        int[] whitePawns = new int[8];
        int[] blackPawns = new int[8];

        for (int i = 0; i < 64; i++) {
            int p = b.sq[i];
            if (p == 0) continue;
            int type = Piece.type(p);
            int v = Piece.value(type);
            int positional = positionalBonus(type, i, p > 0);
            score += Integer.signum(p) * (v + positional);
            if (type == Piece.BISHOP) { if (p > 0) whiteBishops++; else blackBishops++; }
            if (type == Piece.PAWN) { if (p > 0) whitePawns[Board.col(i)]++; else blackPawns[Board.col(i)]++; }
        }

        if (whiteBishops >= 2) score += 35;
        if (blackBishops >= 2) score -= 35;

        score += pawnStructure(b, Side.WHITE, whitePawns, blackPawns);
        score -= pawnStructure(b, Side.BLACK, blackPawns, whitePawns);
        score += rookFileBonus(b, Side.WHITE, whitePawns, blackPawns);
        score -= rookFileBonus(b, Side.BLACK, blackPawns, whitePawns);
        score += kingSafety(b, Side.WHITE);
        score -= kingSafety(b, Side.BLACK);
        score += pieceActivity(b, Side.WHITE, whitePawns, blackPawns);
        score -= pieceActivity(b, Side.BLACK, blackPawns, whitePawns);
        score += threatBonus(b, Side.WHITE);
        score -= threatBonus(b, Side.BLACK);
        score += spaceAdvantage(b, Side.WHITE);
        score -= spaceAdvantage(b, Side.BLACK);
        score += b.turn == Side.WHITE ? 6 : -6;

        score += pseudoMobility(b, Side.WHITE) * 2;
        score -= pseudoMobility(b, Side.BLACK) * 2;
        if (b.isInCheck(Side.BLACK)) score += 35;
        if (b.isInCheck(Side.WHITE)) score -= 35;
        return score;
    }

    static int pawnStructure(Board b, Side side, int[] ownPawns, int[] enemyPawns) {
        int score = 0;
        for (int file = 0; file < 8; file++) {
            if (ownPawns[file] > 1) score -= 14 * (ownPawns[file] - 1);
        }
        for (int i = 0; i < 64; i++) {
            int p = b.sq[i];
            if (p != side.sign * Piece.PAWN) continue;
            int r = Board.row(i), c = Board.col(i);
            boolean isolated = (c == 0 || ownPawns[c - 1] == 0) && (c == 7 || ownPawns[c + 1] == 0);
            if (isolated) score -= 10;
            if (isPassedPawn(b, i, side)) {
                int rankAdvanced = side == Side.WHITE ? 6 - r : r - 1;
                score += 18 + rankAdvanced * rankAdvanced * 4;
            }
        }
        return score;
    }

    static boolean isPassedPawn(Board b, int sq, Side side) {
        int r = Board.row(sq), c = Board.col(sq);
        int dir = side == Side.WHITE ? -1 : 1;
        int enemyPawn = -side.sign * Piece.PAWN;
        for (int rr = r + dir; rr >= 0 && rr < 8; rr += dir) {
            for (int dc = -1; dc <= 1; dc++) {
                int cc = c + dc;
                if (Board.on(rr, cc) && b.sq[Board.idx(rr, cc)] == enemyPawn) return false;
            }
        }
        return true;
    }

    static int rookFileBonus(Board b, Side side, int[] ownPawns, int[] enemyPawns) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            int p = b.sq[i];
            if (p != side.sign * Piece.ROOK) continue;
            int f = Board.col(i);
            if (ownPawns[f] == 0 && enemyPawns[f] == 0) score += 25;
            else if (ownPawns[f] == 0) score += 12;
        }
        return score;
    }

    static int kingSafety(Board b, Side side) {
        int kingSq = -1;
        for (int i = 0; i < 64; i++) if (b.sq[i] == side.sign * Piece.KING) { kingSq = i; break; }
        if (kingSq == -1) return -500;
        int score = 0;
        int r = Board.row(kingSq), c = Board.col(kingSq);
        boolean endgame = nonKingMaterial(b) <= 2400;
        if (!endgame) {
            if ((side == Side.WHITE && r == 7 && (c == 6 || c == 2)) || (side == Side.BLACK && r == 0 && (c == 6 || c == 2))) score += 25;
            int pawnShieldRow = r + (side == Side.WHITE ? -1 : 1);
            for (int dc = -1; dc <= 1; dc++) {
                int cc = c + dc;
                if (Board.on(pawnShieldRow, cc) && b.sq[Board.idx(pawnShieldRow, cc)] == side.sign * Piece.PAWN) score += 10;
                else score -= 6;
            }
        } else {
            int center = 14 - (Math.abs(3 - r) + Math.abs(4 - r) + Math.abs(3 - c) + Math.abs(4 - c));
            score += center * 3;
        }
        return score;
    }

    static int pieceActivity(Board b, Side side, int[] ownPawns, int[] enemyPawns) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            int p = b.sq[i];
            if (p == 0 || Integer.signum(p) != side.sign) continue;
            int type = Piece.type(p);
            int r = side == Side.WHITE ? Board.row(i) : 7 - Board.row(i);
            int c = Board.col(i);

            if (type == Piece.KNIGHT) {
                boolean protectedByPawn = pawnAttacksSquare(b, i, side);
                boolean centralOutpost = r <= 4 && c >= 2 && c <= 5 && protectedByPawn;
                if (centralOutpost) score += 24;
            } else if (type == Piece.BISHOP) {
                score += diagonalReach(b, i) * 2;
            } else if (type == Piece.ROOK) {
                if (r <= 2) score += 10;
                if (ownPawns[c] == 0 && enemyPawns[c] > 0) score += 10;
            } else if (type == Piece.QUEEN) {
                if (r < 5) score += 6;
                if (nonKingMaterial(b) > 5200 && r < 3) score -= 12;
            }
        }
        return score;
    }

    static boolean pawnAttacksSquare(Board b, int target, Side side) {
        int tr = Board.row(target), tc = Board.col(target);
        int fromRow = tr + (side == Side.WHITE ? 1 : -1);
        for (int dc : new int[]{-1, 1}) {
            int fc = tc + dc;
            if (Board.on(fromRow, fc) && b.sq[Board.idx(fromRow, fc)] == side.sign * Piece.PAWN) return true;
        }
        return false;
    }

    static int diagonalReach(Board b, int square) {
        int r = Board.row(square), c = Board.col(square);
        int reach = 0;
        int[][] dirs = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] d : dirs) {
            int rr = r + d[0], cc = c + d[1];
            while (Board.on(rr, cc)) {
                reach++;
                if (b.sq[Board.idx(rr, cc)] != 0) break;
                rr += d[0];
                cc += d[1];
            }
        }
        return reach;
    }

    static int threatBonus(Board b, Side side) {
        int score = 0;
        Side enemy = side.opposite();
        for (int i = 0; i < 64; i++) {
            int p = b.sq[i];
            if (p == 0 || Integer.signum(p) != enemy.sign || Piece.type(p) == Piece.KING) continue;
            if (b.isAttacked(i, side)) {
                int value = Piece.value(Piece.type(p));
                score += Math.min(90, value / 10);
                if (!b.isAttacked(i, enemy)) score += Math.min(70, value / 15);
            }
        }
        return score;
    }

    static int spaceAdvantage(Board b, Side side) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            int p = b.sq[i];
            if (p != side.sign * Piece.PAWN) continue;
            int r = side == Side.WHITE ? Board.row(i) : 7 - Board.row(i);
            int c = Board.col(i);
            if (r <= 3 && c >= 1 && c <= 6) score += 6;
            if (r <= 2 && c >= 2 && c <= 5) score += 8;
        }
        return score;
    }

    static int nonKingMaterial(Board b) {
        int total = 0;
        for (int p : b.sq) if (p != 0 && Piece.type(p) != Piece.KING) total += Piece.value(Piece.type(p));
        return total;
    }

    static int pseudoMobility(Board b, Side side) {
        Board c = b.copy();
        return c.pseudoMoves(side, false).size();
    }

    static int positionalBonus(int type, int sq, boolean white) {
        int r = white ? Board.row(sq) : 7 - Board.row(sq);
        int c = Board.col(sq);
        int center = 14 - (Math.abs(3 - r) + Math.abs(4 - r) + Math.abs(3 - c) + Math.abs(4 - c));
        switch(type) {
            case Piece.PAWN:
                int adv = 6 - r;
                int filePenalty = (c == 0 || c == 7) ? -4 : 0;
                return adv * 7 + center + filePenalty;
            case Piece.KNIGHT:
                int rimPenalty = (r == 0 || r == 7 || c == 0 || c == 7) ? -25 : 0;
                return center * 8 + rimPenalty;
            case Piece.BISHOP:
                return center * 6 + (r < 6 ? 8 : 0);
            case Piece.ROOK:
                return center * 2 + (r == 1 ? 15 : 0);
            case Piece.QUEEN:
                return center * 2;
            case Piece.KING:
                return nonLinearKingTable(r, c);
            default:
                return 0;
        }
    }

    static int nonLinearKingTable(int r, int c) {
        if (r >= 6 && (c <= 2 || c >= 5)) return 20;
        if (r >= 5) return 5;
        return -10;
    }

    static String principalVariation(Board b, Move first, int depth) {
        if (first == null) return "";
        StringBuilder sb = new StringBuilder();
        Board line = b.copy();
        Move current = first;
        for (int i = 0; i < depth && current != null; i++) {
            Move legal = line.findMatchingLegal(new Move(current.from, current.to, current.promotion));
            if (legal == null) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(legal);
            line.makeMoveNoHistory(legal);
            if (line.isGameOver()) break;
            List<Move> moves = line.legalMoves();
            if (moves.isEmpty()) break;
            orderMoves(line, moves);
            int best = -INF;
            Move bestMove = moves.get(0);
            int localDepth = Math.max(1, depth - i - 1);
            for (Move m : moves) {
                Board next = line.copy();
                next.makeMoveNoHistory(m);
                int score = -negamax(next, localDepth - 1, -INF, INF, 1);
                if (score > best) { best = score; bestMove = m; }
            }
            current = bestMove;
        }
        return sb.toString();
    }

    static String formatEval(int cpWhite) {
        if (Math.abs(cpWhite) > MATE - 1000) {
            return cpWhite > 0 ? "White mating" : "Black mating";
        }
        return String.format(Locale.US, "%+.2f", cpWhite / 100.0);
    }
}

class GameReview {
    static class ReviewStats {
        int brilliant, best, excellent, good, inaccuracies, mistakes, blunders;
        void add(String c) {
            if (c.equals("Brilliant")) brilliant++;
            else if (c.equals("Best")) best++;
            else if (c.equals("Excellent")) excellent++;
            else if (c.equals("Good")) good++;
            else if (c.equals("Inaccuracy")) inaccuracies++;
            else if (c.equals("Mistake")) mistakes++;
            else if (c.equals("Blunder")) blunders++;
        }
    }

    static String review(Board played) {
        return review(played, Board.starting().toFen(), AiLevel.ANALYSIS);
    }

    static String review(Board played, String baseFen, AiLevel level) {
        StringBuilder out = new StringBuilder();
        Board b;
        try { b = Board.fromFen(baseFen); }
        catch (Exception ex) { b = Board.starting(); }

        ReviewStats white = new ReviewStats();
        ReviewStats black = new ReviewStats();

        out.append("Game review - stronger local engine\n");
        out.append("Analysis level: ").append(level).append("\n");
        out.append("Final result: ").append(played.resultString()).append("\n");
        out.append("Evaluation: positive = White better, negative = Black better.\n\n");
        out.append(String.format(Locale.US, "%-7s %-11s %-9s %-9s %-9s %-13s %-22s %-20s%n",
                "Move", "Played", "Before", "Best", "After", "Loss", "Comment", "Best line"));
        out.append("----------------------------------------------------------------------------------------------------------------\n");

        int moveNo = 1;
        for (Move original : played.history) {
            Side mover = b.turn;
            int before = ChessAI.evaluate(b);
            ChessAI.SearchResult bestRes = ChessAI.analyze(b, level);
            Move best = bestRes.bestMove;
            int bestWhite = bestRes.scoreWhite;

            Move playedMove = findEquivalentLegalMove(b, original);
            if (playedMove == null) break;
            Board afterBoard = b.copy();
            afterBoard.makeMove(playedMove);
            ChessAI.SearchResult afterRes = ChessAI.analyze(afterBoard, level);
            int afterWhite = afterRes.scoreWhite;

            int loss = mover == Side.WHITE ? bestWhite - afterWhite : afterWhite - bestWhite;
            if (loss < 0) loss = 0;

            boolean sameAsBest = best != null && best.sameUserMove(playedMove);
            String comment = classify(loss, sameAsBest, playedMove, b);
            if (mover == Side.WHITE) white.add(comment); else black.add(comment);

            String ply = mover == Side.WHITE ? (moveNo + ".") : (moveNo + "...");
            out.append(String.format(Locale.US, "%-7s %-11s %-9s %-9s %-9s %-13s %-22s %-20s",
                    ply, playedMove.toString(), ChessAI.formatEval(before), ChessAI.formatEval(bestWhite), ChessAI.formatEval(afterWhite),
                    formatLoss(loss), comment, best == null ? "-" : bestRes.principalVariation));
            out.append("\n");

            b = afterBoard;
            if (mover == Side.BLACK) moveNo++;
        }

        out.append("\nSummary\n");
        out.append("White: ").append(summary(white)).append("\n");
        out.append("Black: ").append(summary(black)).append("\n\n");
        out.append("Notes:\n");
        out.append("- The reviewer now uses alpha-beta search, quiescence search, move ordering and a deeper evaluation.\n");
        out.append("- It is still a local Java engine, not Stockfish, but it is much more useful for finding blunders and better alternatives.\n");
        out.append("- Loss is measured in centipawns compared with the engine's preferred continuation.\n");
        return out.toString();
    }

    static String classify(int loss, boolean sameAsBest, Move playedMove, Board before) {
        if (sameAsBest && isSacrificeThatImproves(playedMove, before)) return "Brilliant";
        if (sameAsBest || loss <= 15) return "Best";
        if (loss <= 35) return "Excellent";
        if (loss <= 80) return "Good";
        if (loss <= 160) return "Inaccuracy";
        if (loss <= 320) return "Mistake";
        return "Blunder";
    }

    static boolean isSacrificeThatImproves(Move m, Board b) {
        int moving = b.sq[m.from];
        int target = b.sq[m.to];
        if (moving == 0 || target == 0) return false;
        return Piece.value(Piece.type(moving)) > Piece.value(Piece.type(target)) + 200;
    }

    static String formatLoss(int loss) {
        if (loss >= ChessAI.MATE - 1000) return "mate swing";
        return String.format(Locale.US, "%.2f", loss / 100.0);
    }

    static String summary(ReviewStats s) {
        return "Brilliant " + s.brilliant + ", Best " + s.best + ", Excellent " + s.excellent +
                ", Good " + s.good + ", Inaccuracies " + s.inaccuracies +
                ", Mistakes " + s.mistakes + ", Blunders " + s.blunders;
    }

    static Move findEquivalentLegalMove(Board b, Move original) {
        for (Move legal : b.legalMoves()) {
            if (legal.from == original.from && legal.to == original.to && legal.promotion == original.promotion) return legal;
        }
        return null;
    }
}

class EloStore {
    private final Path file = Paths.get(System.getProperty("user.home"), "chesspro_elo.properties");
    private final Properties props = new Properties();

    EloStore() { load(); }

    void load() {
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { props.load(in); }
            catch (IOException ignored) {}
        }
        props.putIfAbsent("human", "1200");
        props.putIfAbsent("ai", "1200");
    }

    void save() {
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "ChessPro local Elo ratings");
        } catch (IOException ignored) {}
    }

    int get(String key) { return Integer.parseInt(props.getProperty(key, "1200")); }

    void update(double humanScore) {
        int h = get("human");
        int ai = get("ai");
        int k = 32;
        double expectedH = 1.0 / (1.0 + Math.pow(10, (ai - h) / 400.0));
        double expectedAi = 1.0 - expectedH;
        int newH = (int)Math.round(h + k * (humanScore - expectedH));
        int newAi = (int)Math.round(ai + k * ((1 - humanScore) - expectedAi));
        props.setProperty("human", String.valueOf(newH));
        props.setProperty("ai", String.valueOf(newAi));
        save();
    }

    String label() {
        return "Human Elo: " + get("human") + " | AI Elo: " + get("ai");
    }

    void reset() {
        props.setProperty("human", "1200");
        props.setProperty("ai", "1200");
        save();
    }
}

class Theme {
    final String name;
    final Color light;
    final Color dark;
    final Color bg;
    final Color fg;
    final Color selected;
    final Color panel;
    final Color legal;
    final Color capture;
    final Color lastMove;
    final Color hint;

    Theme(String name, Color light, Color dark, Color bg, Color fg, Color selected, Color panel,
          Color legal, Color capture, Color lastMove, Color hint) {
        this.name = name;
        this.light = light;
        this.dark = dark;
        this.bg = bg;
        this.fg = fg;
        this.selected = selected;
        this.panel = panel;
        this.legal = legal;
        this.capture = capture;
        this.lastMove = lastMove;
        this.hint = hint;
    }

    public String toString() { return name; }

    static Theme[] all() {
        return new Theme[]{
                new Theme("Classic", new Color(238,238,210), new Color(118,150,86), new Color(245,245,245), Color.BLACK,
                        new Color(246,246,105), Color.WHITE, new Color(185,220,120), new Color(235,120,120), new Color(245,220,120), new Color(120,180,255)),
                new Theme("Dark", new Color(90,90,90), new Color(45,45,45), new Color(25,25,25), new Color(235,235,235),
                        new Color(80,120,180), new Color(35,35,35), new Color(70,130,90), new Color(155,70,70), new Color(110,100,60), new Color(70,110,160)),
                new Theme("Blue", new Color(220,235,250), new Color(80,130,185), new Color(235,242,250), new Color(20,35,50),
                        new Color(255,220,100), Color.WHITE, new Color(150,220,250), new Color(255,145,145), new Color(255,235,130), new Color(110,160,255)),
                new Theme("Citrus", new Color(255,249,202), new Color(245,190,50), new Color(255,253,234), new Color(60,45,0),
                        new Color(120,210,90), new Color(255,255,245), new Color(180,225,90), new Color(245,110,80), new Color(255,235,130), new Color(110,200,120)),
                new Theme("Midnight Neon", new Color(39, 53, 75), new Color(15, 23, 42), new Color(8, 13, 26), new Color(226, 232, 240),
                        new Color(56, 189, 248), new Color(17, 24, 39), new Color(34, 197, 94), new Color(248, 113, 113), new Color(99, 102, 241), new Color(168, 85, 247)),
                new Theme("Tournament Wood", new Color(222, 184, 135), new Color(139, 90, 43), new Color(244, 236, 220), new Color(50, 34, 18),
                        new Color(255, 215, 0), new Color(255, 248, 230), new Color(116, 185, 80), new Color(214, 80, 70), new Color(255, 226, 120), new Color(64, 160, 220)),
                new Theme("Arctic", new Color(236, 248, 255), new Color(130, 180, 215), new Color(241, 250, 255), new Color(16, 48, 72),
                        new Color(130, 210, 255), Color.WHITE, new Color(140, 220, 210), new Color(255, 120, 140), new Color(190, 230, 255), new Color(110, 140, 255)),
                new Theme("Rose Gold", new Color(255, 237, 232), new Color(190, 120, 105), new Color(255, 248, 246), new Color(70, 35, 35),
                        new Color(255, 183, 178), new Color(255, 250, 248), new Color(165, 214, 167), new Color(239, 118, 122), new Color(255, 220, 180), new Color(255, 160, 190)),
                new Theme("Emerald", new Color(220, 244, 225), new Color(27, 120, 84), new Color(236, 253, 245), new Color(6, 78, 59),
                        new Color(250, 204, 21), new Color(240, 253, 244), new Color(134, 239, 172), new Color(248, 113, 113), new Color(187, 247, 208), new Color(45, 212, 191)),
                new Theme("Slate", new Color(203, 213, 225), new Color(71, 85, 105), new Color(241, 245, 249), new Color(15, 23, 42),
                        new Color(251, 191, 36), Color.WHITE, new Color(125, 211, 252), new Color(252, 165, 165), new Color(226, 232, 240), new Color(196, 181, 253)),
                new Theme("Coffee", new Color(232, 218, 196), new Color(112, 79, 56), new Color(250, 245, 238), new Color(55, 35, 20),
                        new Color(245, 158, 11), new Color(255, 251, 245), new Color(139, 195, 74), new Color(211, 85, 85), new Color(238, 207, 148), new Color(98, 170, 220)),
                new Theme("Grape", new Color(237, 233, 254), new Color(124, 58, 237), new Color(250, 245, 255), new Color(49, 46, 129),
                        new Color(251, 191, 36), new Color(255,255,255), new Color(134, 239, 172), new Color(251, 113, 133), new Color(216, 180, 254), new Color(147, 197, 253)),
                new Theme("Ocean", new Color(204, 251, 241), new Color(14, 116, 144), new Color(236, 254, 255), new Color(8, 47, 73),
                        new Color(252, 211, 77), Color.WHITE, new Color(103, 232, 249), new Color(251, 113, 133), new Color(165, 243, 252), new Color(96, 165, 250)),
                new Theme("Graphite", new Color(156, 163, 175), new Color(55, 65, 81), new Color(17, 24, 39), new Color(243, 244, 246),
                        new Color(250, 204, 21), new Color(31, 41, 55), new Color(74, 222, 128), new Color(248, 113, 113), new Color(107, 114, 128), new Color(96, 165, 250)),
                new Theme("Candy", new Color(255, 228, 230), new Color(251, 113, 133), new Color(255, 241, 242), new Color(136, 19, 55),
                        new Color(253, 224, 71), Color.WHITE, new Color(186, 230, 253), new Color(244, 114, 182), new Color(254, 205, 211), new Color(216, 180, 254)),
                new Theme("Olive", new Color(236, 236, 199), new Color(107, 114, 35), new Color(254, 252, 232), new Color(63, 63, 30),
                        new Color(250, 204, 21), new Color(255, 255, 245), new Color(190, 242, 100), new Color(248, 113, 113), new Color(217, 249, 157), new Color(125, 211, 252)),
                new Theme("Lava", new Color(254, 215, 170), new Color(194, 65, 12), new Color(255, 247, 237), new Color(67, 20, 7),
                        new Color(253, 224, 71), Color.WHITE, new Color(132, 204, 22), new Color(220, 38, 38), new Color(251, 146, 60), new Color(59, 130, 246)),
                new Theme("Royal", new Color(224, 231, 255), new Color(67, 56, 202), new Color(238, 242, 255), new Color(30, 27, 75),
                        new Color(250, 204, 21), Color.WHITE, new Color(134, 239, 172), new Color(248, 113, 113), new Color(199, 210, 254), new Color(147, 197, 253)),
                new Theme("Mono Print", new Color(245, 245, 245), new Color(155, 155, 155), Color.WHITE, Color.BLACK,
                        new Color(255, 230, 120), Color.WHITE, new Color(210, 210, 210), new Color(160, 160, 160), new Color(225, 225, 225), new Color(180, 180, 180)),
                new Theme("Alpine", new Color(230, 245, 235), new Color(67, 126, 93), new Color(247, 252, 248), new Color(25, 64, 45),
                        new Color(177, 235, 128), Color.WHITE, new Color(156, 230, 190), new Color(255, 132, 132), new Color(197, 235, 180), new Color(108, 178, 255)),
                new Theme("Cyberpunk", new Color(60, 20, 85), new Color(11, 14, 31), new Color(6, 8, 24), new Color(243, 232, 255),
                        new Color(236, 72, 153), new Color(18, 18, 39), new Color(34, 211, 238), new Color(251, 113, 133), new Color(124, 58, 237), new Color(250, 204, 21)),
                new Theme("Sandstone", new Color(250, 231, 190), new Color(181, 125, 68), new Color(255, 250, 240), new Color(90, 55, 20),
                        new Color(245, 158, 11), Color.WHITE, new Color(196, 235, 150), new Color(239, 100, 84), new Color(255, 222, 150), new Color(93, 180, 230)),
                new Theme("Forest Pro", new Color(214, 232, 202), new Color(46, 97, 66), new Color(240, 248, 240), new Color(24, 56, 35),
                        new Color(234, 179, 8), new Color(252, 255, 247), new Color(132, 204, 22), new Color(248, 113, 113), new Color(190, 230, 160), new Color(45, 212, 191)),
                new Theme("Ice Blue", new Color(232, 244, 255), new Color(96, 165, 250), new Color(248, 252, 255), new Color(20, 55, 90),
                        new Color(186, 230, 253), Color.WHITE, new Color(125, 211, 252), new Color(251, 113, 133), new Color(219, 234, 254), new Color(147, 197, 253)),
                new Theme("Burgundy", new Color(255, 228, 230), new Color(127, 29, 29), new Color(255, 247, 247), new Color(69, 10, 10),
                        new Color(253, 186, 116), Color.WHITE, new Color(187, 247, 208), new Color(248, 113, 113), new Color(254, 202, 202), new Color(216, 180, 254)),
                new Theme("Mint", new Color(220, 252, 231), new Color(52, 211, 153), new Color(240, 253, 250), new Color(6, 78, 59),
                        new Color(250, 204, 21), Color.WHITE, new Color(110, 231, 183), new Color(251, 113, 133), new Color(187, 247, 208), new Color(103, 232, 249)),
                new Theme("Terminal", new Color(25, 40, 25), new Color(4, 20, 4), new Color(1, 10, 1), new Color(167, 243, 208),
                        new Color(34, 197, 94), new Color(5, 20, 8), new Color(74, 222, 128), new Color(248, 113, 113), new Color(22, 101, 52), new Color(132, 204, 22)),
                new Theme("Sunset", new Color(255, 237, 213), new Color(234, 88, 12), new Color(255, 247, 237), new Color(67, 20, 7),
                        new Color(252, 211, 77), Color.WHITE, new Color(134, 239, 172), new Color(244, 63, 94), new Color(253, 186, 116), new Color(192, 132, 252)),
                new Theme("Space", new Color(51, 65, 85), new Color(15, 23, 42), new Color(2, 6, 23), new Color(226, 232, 240),
                        new Color(56, 189, 248), new Color(15, 23, 42), new Color(34, 197, 94), new Color(251, 113, 133), new Color(71, 85, 105), new Color(129, 140, 248)),
                new Theme("School", new Color(255, 255, 235), new Color(78, 116, 176), new Color(250, 252, 255), new Color(30, 41, 59),
                        new Color(252, 211, 77), Color.WHITE, new Color(190, 242, 100), new Color(251, 113, 133), new Color(219, 234, 254), new Color(147, 197, 253))
        };
    }
}


class OpeningBook {
    static class BookLine {
        final String[] moves;
        final String name;
        BookLine(String sequence, String name) {
            this.moves = sequence.trim().split("\\s+");
            this.name = name;
        }
    }

    static final Random RNG = new Random(73);
    static final List<BookLine> LINES = new ArrayList<>();

    // Opening book is now loaded from an external txt file instead of a huge Java String array.
    // The file is created automatically in the folder from which you start the program.
    // File name: chesspro_openings.txt
    // Format per line: coordinate-move sequence | opening name / training label
    static final String BOOK_FILE_NAME = "chesspro_openings.txt";
    static final String DEFAULT_OPENING_BOOK = """
# ChessPro opening book
# This file is created automatically if it does not exist.
# Put one opening line per row.
# Format:
# coordinate-move sequence | opening name / training label
#
# Example:
e2e4 e7e5 g1f3 b8c6 f1b5 | Ruy Lopez example
d2d4 d7d5 c2c4 | Queen's Gambit example
""";

    static Path openingBookPath() {
        return Paths.get(System.getProperty("user.dir"), BOOK_FILE_NAME);
    }

    static void ensureOpeningBookFileExists(Path path) throws IOException {
        if (Files.exists(path)) return;
        Files.write(path, DEFAULT_OPENING_BOOK.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW);
        System.out.println("Created opening book file: " + path.toAbsolutePath());
        System.out.println("Paste your old DATA_CHUNKS lines into this txt file, one opening line per row.");
    }

    static List<String> readOpeningBookRows() {
        Path path = openingBookPath();
        try {
            ensureOpeningBookFileExists(path);
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("Could not read " + BOOK_FILE_NAME + ": " + ex.getMessage());
            System.err.println("Using the small built-in fallback opening book.");
            return Arrays.asList(DEFAULT_OPENING_BOOK.split("\\R"));
        }
    }

    static {
        loadBookData();
    }

    static void loadBookData() {
        LINES.clear();
        for (String row : readOpeningBookRows()) {
            parseBookRow(row);
        }

        // Safety fallback: if the txt file exists but is empty or malformed, keep the book usable.
        if (LINES.isEmpty()) {
            for (String row : DEFAULT_OPENING_BOOK.split("\\R")) {
                parseBookRow(row);
            }
        }
    }

    static void parseBookRow(String row) {
        if (row == null) return;
        row = row.trim();
        if (row.isEmpty() || row.startsWith("#")) return;
        int bar = row.lastIndexOf('|');
        if (bar < 0) return;
        String sequence = row.substring(0, bar).trim();
        String name = row.substring(bar + 1).trim();
        if (!sequence.isEmpty() && !name.isEmpty()) add(sequence, name);
    }

    static void add(String sequence, String name) {
        LINES.add(new BookLine(sequence, name));
    }

    static Move choose(Board board) {
        if (board == null || board.history.size() > 24) return null;
        List<String> played = historyCoordinates(board);
        List<String> candidates = new ArrayList<>();
        for (BookLine line : LINES) {
            if (line.moves.length <= played.size()) continue;
            boolean prefix = true;
            for (int i = 0; i < played.size(); i++) {
                if (!line.moves[i].equals(played.get(i))) {
                    prefix = false;
                    break;
                }
            }
            if (prefix) candidates.add(line.moves[played.size()]);
        }
        Collections.shuffle(candidates, RNG);
        for (String coord : candidates) {
            Move move = fromCoordinate(board, coord);
            if (move != null) return move;
        }
        return null;
    }

    static String currentName(Board board) {
        if (board == null) return "-";
        List<String> played = historyCoordinates(board);
        String best = "-";
        int bestLen = -1;
        for (BookLine line : LINES) {
            int matched = 0;
            while (matched < played.size() && matched < line.moves.length && line.moves[matched].equals(played.get(matched))) {
                matched++;
            }
            if (matched > bestLen) {
                bestLen = matched;
                best = line.name;
            }
        }
        return bestLen <= 0 ? "Out of book" : best;
    }

    static List<String> historyCoordinates(Board board) {
        List<String> out = new ArrayList<>();
        for (Move m : board.history) out.add(m.coordinate());
        return out;
    }

    static Move fromCoordinate(Board board, String coord) {
        if (coord == null || coord.length() < 4) return null;
        int from = Board.squareFromName(coord.substring(0, 2));
        int to = Board.squareFromName(coord.substring(2, 4));
        if (from < 0 || to < 0) return null;
        int promo = 0;
        if (coord.length() >= 5) {
            char ch = Character.toLowerCase(coord.charAt(4));
            if (ch == 'q') promo = board.turn.sign * Piece.QUEEN;
            if (ch == 'r') promo = board.turn.sign * Piece.ROOK;
            if (ch == 'b') promo = board.turn.sign * Piece.BISHOP;
            if (ch == 'n') promo = board.turn.sign * Piece.KNIGHT;
        }
        return board.findMatchingLegal(new Move(from, to, promo));
    }
}
class TrainingPuzzle {
    final String title;
    final String fen;
    final String solution;
    final String explanation;

    TrainingPuzzle(String title, String fen, String solution, String explanation) {
        this.title = title;
        this.fen = fen;
        this.solution = solution;
        this.explanation = explanation;
    }
}

class PuzzleBook {
    static final TrainingPuzzle[] PUZZLES = new TrainingPuzzle[] {
            new TrainingPuzzle("Start correctly", Board.starting().toFen(), "e2e4", "Fight for the centre and open lines for the bishop and queen."),
            new TrainingPuzzle("Develop a knight", "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", "e7e5", "Black also takes space in the centre."),
            new TrainingPuzzle("Castle for safety", "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 2 3", "e1g1", "White has developed pieces and can castle."),
            new TrainingPuzzle("Queen's Gambit setup", "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq c3 0 2", "e7e6", "Black supports the d5 pawn and opens the bishop."),
            new TrainingPuzzle("Use the open file", "6k1/5ppp/8/8/8/8/5PPP/5RK1 w - - 0 1", "f1f8", "The rook uses the open f-file to give a forcing check."),
            new TrainingPuzzle("Central break", "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq e6 0 2", "d2d4", "White challenges the centre immediately."),
            new TrainingPuzzle("Recapture toward centre", "rnbqkbnr/pppp1ppp/8/4p3/3PP3/8/PPP2PPP/RNBQKBNR b KQkq d3 0 2", "e5d4", "Black removes White's central pawn."),
            new TrainingPuzzle("Develop before attacking", "rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 1 2", "b1c3", "A natural developing move protects the e4 pawn."),
            new TrainingPuzzle("Build London shape", "rnbqkbnr/ppp1pppp/8/3p4/3P4/5N2/PPP1PPPP/RNBQKB1R w KQkq - 1 2", "c1f4", "The London bishop comes out before e3."),
            new TrainingPuzzle("Fianchetto", "rnbqkbnr/pppppppp/8/8/8/6P1/PPPPPP1P/RNBQKBNR b KQkq - 0 1", "d7d5", "Black takes central space against a flank opening.")
    };

    static TrainingPuzzle randomPuzzle() {
        return PUZZLES[new Random().nextInt(PUZZLES.length)];
    }
}

class Perft {
    static long nodes(Board board, int depth) {
        if (depth <= 0) return 1;
        long total = 0;
        for (Move m : board.legalMoves()) {
            Board next = board.copy();
            next.makeMoveNoHistory(m);
            total += nodes(next, depth - 1);
        }
        return total;
    }

    static String divide(Board board, int depth) {
        StringBuilder sb = new StringBuilder();
        long total = 0;
        List<Move> moves = board.legalMoves();
        for (Move m : moves) {
            Board next = board.copy();
            next.makeMoveNoHistory(m);
            long n = nodes(next, Math.max(0, depth - 1));
            total += n;
            sb.append(String.format(Locale.US, "%-8s %,d%n", m.coordinate(), n));
        }
        sb.append(String.format(Locale.US, "%nTotal: %,d", total));
        return sb.toString();
    }
}

class UiColors {
    static Color blend(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int)Math.round(a.getRed() * (1 - t) + b.getRed() * t);
        int g = (int)Math.round(a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl = (int)Math.round(a.getBlue() * (1 - t) + b.getBlue() * t);
        return new Color(r, g, bl);
    }

    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}

class HeaderPanel extends JPanel {
    Theme theme;
    JLabel title = new JLabel("ChessPro Ultimate");
    JLabel subtitle = new JLabel("Cleaner interface, stronger Java engine, training, review, FEN/PGN and analysis tools");
    JLabel right = new JLabel("Ready");

    HeaderPanel(Theme theme) {
        this.theme = theme;
        setLayout(new BorderLayout(16, 0));
        setBorder(new EmptyBorder(18, 22, 18, 22));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        right.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        add(text, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    void setTheme(Theme t) {
        this.theme = t;
        title.setForeground(t.fg);
        subtitle.setForeground(t.fg);
        right.setForeground(t.fg);
        repaint();
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color start = UiColors.blend(theme.bg, theme.panel, 0.25);
        Color end = UiColors.blend(theme.bg, theme.selected, 0.10);
        g2.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);
        g2.setColor(UiColors.withAlpha(theme.fg, 25));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 28, 28);
        g2.dispose();
    }
}

class StatusCard extends JPanel {
    JLabel title = new JLabel();
    JLabel value = new JLabel();
    Theme theme;

    StatusCard(String title, String value, Theme theme) {
        this.theme = theme;
        this.title.setText(title);
        this.value.setText(value);
        setLayout(new BorderLayout(2, 3));
        setBorder(new EmptyBorder(10, 12, 10, 12));
        this.title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        this.value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        add(this.title, BorderLayout.NORTH);
        add(this.value, BorderLayout.CENTER);
        setTheme(theme);
    }

    void setTheme(Theme t) {
        this.theme = t;
        title.setForeground(UiColors.withAlpha(t.fg, 160));
        value.setForeground(t.fg);
        setOpaque(false);
        repaint();
    }

    void setValue(String v) { value.setText(v); }

    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(UiColors.blend(theme.panel, theme.bg, 0.12));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        g2.setColor(UiColors.withAlpha(theme.fg, 30));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g2.dispose();
        super.paintComponent(g);
    }
}

class BoardCanvas extends JPanel {
    final ChessFrame frame;
    int boardX, boardY, square;
    Font pieceFont = new Font("Serif", Font.PLAIN, 64);
    Font coordFont = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    BoardCanvas(ChessFrame frame) {
        this.frame = frame;
        setPreferredSize(new Dimension(760, 760));
        setMinimumSize(new Dimension(540, 540));
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                requestFocusInWindow();
                int sq = squareAt(e.getX(), e.getY());
                if (sq >= 0) frame.squareClicked(sq);
            }
        });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseMoved(java.awt.event.MouseEvent e) {
                setToolTipText(squareTooltip(squareAt(e.getX(), e.getY())));
            }
        });
    }

    String squareTooltip(int sq) {
        if (sq < 0) return null;
        int p = frame.board.sq[sq];
        return Board.squareName(sq) + (p == 0 ? "" : " - " + (p > 0 ? "White " : "Black ") + Piece.name(p));
    }

    int squareAt(int x, int y) {
        if (square <= 0) return -1;
        int c = (x - boardX) / square;
        int r = (y - boardY) / square;
        if (c < 0 || c > 7 || r < 0 || r > 7) return -1;
        if (frame.flipBox.isSelected()) {
            r = 7 - r;
            c = 7 - c;
        }
        return Board.idx(r, c);
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int size = Math.min(getWidth() - 26, getHeight() - 26);
        square = size / 8;
        int boardSize = square * 8;
        boardX = (getWidth() - boardSize) / 2;
        boardY = (getHeight() - boardSize) / 2;

        g2.setColor(new Color(0, 0, 0, 42));
        g2.fillRoundRect(boardX + 8, boardY + 10, boardSize, boardSize, 30, 30);
        g2.setColor(frame.theme.panel);
        g2.fillRoundRect(boardX - 10, boardY - 10, boardSize + 20, boardSize + 20, 30, 30);
        g2.setColor(UiColors.withAlpha(frame.theme.fg, 40));
        g2.drawRoundRect(boardX - 10, boardY - 10, boardSize + 20, boardSize + 20, 30, 30);

        Set<Integer> legalTargets = frame.legalTargets();
        Set<Integer> legalCaptures = frame.legalCaptures();
        int lastFrom = -1, lastTo = -1;
        if (!frame.board.history.isEmpty()) {
            Move last = frame.board.history.get(frame.board.history.size() - 1);
            lastFrom = last.from;
            lastTo = last.to;
        }

        for (int vr = 0; vr < 8; vr++) {
            for (int vc = 0; vc < 8; vc++) {
                int br = frame.flipBox.isSelected() ? 7 - vr : vr;
                int bc = frame.flipBox.isSelected() ? 7 - vc : vc;
                int sqIndex = Board.idx(br, bc);
                int x = boardX + vc * square;
                int y = boardY + vr * square;
                boolean light = (br + bc) % 2 == 0;
                Color base = light ? frame.theme.light : frame.theme.dark;
                g2.setColor(base);
                g2.fillRect(x, y, square, square);

                if (sqIndex == lastFrom || sqIndex == lastTo) overlay(g2, x, y, frame.theme.lastMove, 155);
                if (sqIndex == frame.hintFrom || sqIndex == frame.hintTo) overlay(g2, x, y, frame.theme.hint, 155);
                if (legalTargets.contains(sqIndex)) overlay(g2, x, y, legalCaptures.contains(sqIndex) ? frame.theme.capture : frame.theme.legal, 150);
                if (sqIndex == frame.selected) overlay(g2, x, y, frame.theme.selected, 185);

                if (frame.coordsBox.isSelected()) drawCoordinate(g2, sqIndex, x, y, light);
            }
        }

        for (int vr = 0; vr < 8; vr++) {
            for (int vc = 0; vc < 8; vc++) {
                int br = frame.flipBox.isSelected() ? 7 - vr : vr;
                int bc = frame.flipBox.isSelected() ? 7 - vc : vc;
                int sqIndex = Board.idx(br, bc);
                int p = frame.board.sq[sqIndex];
                if (p != 0) drawPiece(g2, p, boardX + vc * square, boardY + vr * square);
            }
        }

        g2.setColor(UiColors.withAlpha(frame.theme.fg, 80));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(boardX, boardY, boardSize, boardSize, 10, 10);
        g2.dispose();
    }

    void overlay(Graphics2D g2, int x, int y, Color c, int alpha) {
        g2.setColor(UiColors.withAlpha(c, alpha));
        g2.fillRect(x, y, square, square);
    }

    void drawCoordinate(Graphics2D g2, int idx, int x, int y, boolean light) {
        g2.setFont(coordFont);
        g2.setColor(light ? UiColors.blend(frame.theme.dark, Color.BLACK, 0.2) : UiColors.blend(frame.theme.light, Color.WHITE, 0.1));
        String name = Board.squareName(idx);
        g2.drawString(name, x + 6, y + square - 7);
    }

    void drawPiece(Graphics2D g2, int p, int x, int y) {
        String s = Piece.unicode(p);
        int fs = Math.max(28, (int)(square * 0.72));
        pieceFont = new Font("Serif", Font.PLAIN, fs);
        g2.setFont(pieceFont);
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (square - fm.stringWidth(s)) / 2;
        int ty = y + (square - fm.getHeight()) / 2 + fm.getAscent() - 1;
        Color main = p > 0 ? new Color(248, 248, 238) : new Color(18, 18, 20);
        Color outline = p > 0 ? new Color(35, 35, 35) : new Color(245, 245, 235);
        g2.setColor(UiColors.withAlpha(outline, 210));
        g2.drawString(s, tx - 1, ty);
        g2.drawString(s, tx + 1, ty);
        g2.drawString(s, tx, ty - 1);
        g2.drawString(s, tx, ty + 1);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.drawString(s, tx + 3, ty + 4);
        g2.setColor(main);
        g2.drawString(s, tx, ty);
    }
}

class ChessFrame extends JFrame {
    Board board = Board.starting();
    final EloStore elo = new EloStore();
    final Deque<Move> redoStack = new ArrayDeque<>();

    Theme theme = Theme.all()[0];
    String baseFen = Board.starting().toFen();
    boolean aiThinking = false;
    int selected = -1;
    int hintFrom = -1;
    int hintTo = -1;

    HeaderPanel header;
    BoardCanvas boardCanvas;
    JPanel root;
    JPanel rightPanel;
    JTabbedPane tabs;
    DefaultListModel<String> moveListModel = new DefaultListModel<>();
    JList<String> moveList = new JList<>(moveListModel);

    JComboBox<GameMode> modeBox = new JComboBox<>(GameMode.values());
    JComboBox<AiLevel> aiBox = new JComboBox<>(AiLevel.values());
    JComboBox<Theme> themeBox = new JComboBox<>(Theme.all());
    JCheckBox flipBox = new JCheckBox("Flip board");
    JCheckBox coordsBox = new JCheckBox("Coordinates", true);
    JCheckBox highlightBox = new JCheckBox("Legal moves", true);
    JCheckBox bookBox = new JCheckBox("Use opening book", true);
    JCheckBox autoAnalyzeBox = new JCheckBox("Auto static eval", true);

    StatusCard turnCard;
    StatusCard evalCard;
    StatusCard materialCard;
    StatusCard bookCard;
    StatusCard eloCard;
    StatusCard fenCard;

    JTextArea analysisArea = new JTextArea();
    JTextArea reviewArea = new JTextArea();
    JTextArea toolsArea = new JTextArea();
    JTextArea notesArea = new JTextArea();

    TrainingPuzzle currentPuzzle;
    boolean puzzleActive = false;

    ChessFrame() {
        super("ChessPro Ultimate Coach - Java Chess Simulator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1220, 820));
        setSize(1360, 880);
        setLocationRelativeTo(null);
        buildUi();
        newGame();
    }

    void buildUi() {
        root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        header = new HeaderPanel(theme);
        root.add(header, BorderLayout.NORTH);

        boardCanvas = new BoardCanvas(this);
        JPanel boardWrap = new JPanel(new GridBagLayout());
        boardWrap.setOpaque(false);
        boardWrap.add(boardCanvas);
        root.add(boardWrap, BorderLayout.CENTER);

        rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setPreferredSize(new Dimension(430, 760));
        rightPanel.setBorder(new EmptyBorder(0, 4, 0, 0));
        tabs = new JTabbedPane();
        tabs.addTab("Home", buildHomeTab());
        tabs.addTab("Play", buildPlayTab());
        tabs.addTab("Analysis", buildAnalysisTab());
        tabs.addTab("Review", buildReviewTab());
        tabs.addTab("Tools", buildToolsTab());
        tabs.addTab("Notes", buildNotesTab());
        rightPanel.add(tabs, BorderLayout.CENTER);
        root.add(rightPanel, BorderLayout.EAST);

        applyTheme();
        bindShortcuts();
    }

    JPanel buildHomeTab() {
        JPanel p = verticalPanel();

        JLabel intro = new JLabel("<html><b>Quick start</b><br>"
                + "Choose one of these presets instead of configuring everything by hand. "
                + "You can still change all settings later in the Play tab.</html>");
        intro.setBorder(new EmptyBorder(4, 4, 10, 4));

        p.add(section("Start here", intro,
                buttonRow(button("Play White - Easy", e -> startPreset(GameMode.HUMAN_VS_AI, AiLevel.NOVICE)),
                        button("Play White - Club", e -> startPreset(GameMode.HUMAN_VS_AI, AiLevel.CLUB))),
                buttonRow(button("Play Black - Club", e -> startPreset(GameMode.AI_VS_HUMAN, AiLevel.CLUB)),
                        button("Play Black - Master", e -> startPreset(GameMode.AI_VS_HUMAN, AiLevel.MASTER))),
                buttonRow(button("Two players", e -> startPreset(GameMode.HUMAN_VS_HUMAN, AiLevel.CLUB)),
                        button("Watch AI match", e -> startPreset(GameMode.AI_VS_AI, AiLevel.POSITIONAL)))));

        p.add(section("Training shortcuts",
                buttonRow(button("Random puzzle", e -> startPuzzle()),
                        button("Analyze position", e -> analyzePosition())),
                buttonRow(button("Review game", e -> showReview()),
                        button("Show legal moves", e -> listLegalMoves()))));

        JTextArea guide = new JTextArea();
        guide.setEditable(false);
        guide.setLineWrap(true);
        guide.setWrapStyleWord(true);
        guide.setText("Simple workflow:\n"
                + "1. Start a preset above.\n"
                + "2. Click a piece and then a target square. Legal targets are highlighted.\n"
                + "3. Use Hint when you are stuck.\n"
                + "4. After the game, use Review for mistakes, blunders and better lines.\n"
                + "5. Use Analysis for top engine candidates in any position.");
        p.add(section("How to use it", scroll(guide, 385, 170)));
        return scrollable(p);
    }

    void startPreset(GameMode mode, AiLevel level) {
        modeBox.setSelectedItem(mode);
        aiBox.setSelectedItem(level);
        newGame();
        tabs.setSelectedIndex(1);
    }

    JPanel buildPlayTab() {
        JPanel p = verticalPanel();
        p.add(section("Setup", labeled("Mode", modeBox), labeled("AI opponent", aiBox), labeled("Theme", themeBox), row(flipBox, coordsBox), row(highlightBox, bookBox), row(autoAnalyzeBox)));
        p.add(section("Game", buttonRow(button("New", e -> newGame()), button("Undo", e -> undoPly()), button("Redo", e -> redoPly())), buttonRow(button("Hint", e -> showHint()), button("Analyze", e -> analyzePosition()), button("Book", e -> playBookMove()))));
        p.add(section("Position", statusGrid()));
        moveList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        moveList.setVisibleRowCount(12);
        p.add(section("Move list", scroll(moveList, 370, 210)));
        wireControls();
        return scrollable(p);
    }

    JPanel buildAnalysisTab() {
        JPanel p = verticalPanel();
        analysisArea.setEditable(false);
        analysisArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        analysisArea.setLineWrap(false);
        p.add(section("Engine",
                buttonRow(button("Analyze current", e -> analyzePosition()), button("Top moves", e -> showTopMoves()), button("Hint", e -> showHint())),
                buttonRow(button("Coach advice", e -> showCoachAdvice()), button("Copy analysis", e -> copyAnalysis()), button("Clear", e -> analysisArea.setText(""))),
                scroll(analysisArea, 380, 500)));
        return scrollable(p);
    }

    JPanel buildReviewTab() {
        JPanel p = verticalPanel();
        reviewArea.setEditable(false);
        reviewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        p.add(section("Game review", buttonRow(button("Review game", e -> showReview()), button("Save review", e -> saveReview()), button("Copy PGN", e -> copyPgn())), scroll(reviewArea, 385, 590)));
        return scrollable(p);
    }

    JPanel buildToolsTab() {
        JPanel p = verticalPanel();
        toolsArea.setEditable(false);
        toolsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        p.add(section("Files", buttonRow(button("Copy FEN", e -> copyFen()), button("Load FEN", e -> loadFen())), buttonRow(button("Copy PGN", e -> copyPgn()), button("Save PGN", e -> exportPgn())), buttonRow(button("Board PNG", e -> saveBoardImage()), button("ASCII", e -> copyAsciiBoard()))));
        p.add(section("Training and validation",
                buttonRow(button("Puzzle", e -> startPuzzle()), button("Show solution", e -> showPuzzleSolution()), button("Legal moves", e -> listLegalMoves())),
                buttonRow(button("Copy legal", e -> copyLegalMoves()), button("Reset Elo", e -> resetElo()), button("Coach advice", e -> showCoachAdvice())),
                buttonRow(button("Perft 1", e -> runPerft(1)), button("Perft 2", e -> runPerft(2)), button("Perft 3", e -> runPerft(3))),
                scroll(toolsArea, 380, 350)));
        return scrollable(p);
    }

    JPanel buildNotesTab() {
        JPanel p = verticalPanel();
        notesArea.setText(defaultNotes());
        notesArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        p.add(section("Coach notes", scroll(notesArea, 385, 640)));
        return scrollable(p);
    }

    String defaultNotes() {
        return "Training checklist:\n\n" +
                "1. In the opening, fight for the centre with pawns and pieces.\n" +
                "2. Develop knights and bishops before moving the queen too often.\n" +
                "3. Castle before opening the centre when your king is still exposed.\n" +
                "4. After every move ask: what is attacked, what is defended, and what changed?\n" +
                "5. Use Review after a game. The loss column estimates how much worse the move was than the engine's best line.\n" +
                "6. Use Perft only for testing move generation. From the normal start position, depth 1 should be 20 and depth 2 should be 400.\n" +
                "7. Stronger AI levels use iterative deepening, quiescence search, killer moves, history heuristics and a transposition table.\n" +
                "8. The Home tab is intended for simple preset starts; the Play tab is for detailed control.";
    }

    void wireControls() {
        modeBox.addActionListener(e -> newGame());
        aiBox.setSelectedItem(AiLevel.CLUB);
        themeBox.addActionListener(e -> {
            theme = (Theme) themeBox.getSelectedItem();
            applyTheme();
            refresh();
        });
        flipBox.addActionListener(e -> refresh());
        coordsBox.addActionListener(e -> refresh());
        highlightBox.addActionListener(e -> refresh());
    }

    void bindShortcuts() {
        JRootPane rp = getRootPane();
        bind(rp, "control N", "new", () -> newGame());
        bind(rp, "control Z", "undo", () -> undoPly());
        bind(rp, "control Y", "redo", () -> redoPly());
        bind(rp, "control H", "hint", () -> showHint());
        bind(rp, "control A", "analyze", () -> analyzePosition());
        bind(rp, "control F", "fen", () -> copyFen());
        bind(rp, "control P", "pgn", () -> copyPgn());
        bind(rp, "F", "flip", () -> { flipBox.setSelected(!flipBox.isSelected()); refresh(); });
    }

    void bind(JComponent c, String stroke, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), name);
        c.getActionMap().put(name, new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { action.run(); } });
    }

    JPanel statusGrid() {
        JPanel grid = new JPanel(new GridLayout(3, 2, 8, 8));
        grid.setOpaque(false);
        turnCard = new StatusCard("Turn", "White", theme);
        evalCard = new StatusCard("Eval", "0.00", theme);
        materialCard = new StatusCard("Material", "Equal", theme);
        bookCard = new StatusCard("Opening", "Start", theme);
        eloCard = new StatusCard("Elo", elo.label(), theme);
        fenCard = new StatusCard("FEN", "-", theme);
        grid.add(turnCard);
        grid.add(evalCard);
        grid.add(materialCard);
        grid.add(bookCard);
        grid.add(eloCard);
        grid.add(fenCard);
        return grid;
    }

    JPanel verticalPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.setOpaque(false);
        return p;
    }

    JPanel scrollable(JPanel content) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        holder.add(sp, BorderLayout.CENTER);
        return holder;
    }

    JScrollPane scroll(Component c, int w, int h) {
        JScrollPane sp = new JScrollPane(c);
        sp.setPreferredSize(new Dimension(w, h));
        sp.setBorder(BorderFactory.createEmptyBorder());
        return sp;
    }

    JPanel section(String title, Component... children) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title), new EmptyBorder(8, 8, 8, 8)));
        for (Component child : children) {
            if (child instanceof JComponent) ((JComponent)child).setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(child);
            p.add(Box.createVerticalStrut(8));
        }
        return p;
    }

    JPanel labeled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(4, 3));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(390, 58));
        return p;
    }

    JPanel row(Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        for (Component c : comps) p.add(c);
        return p;
    }

    JPanel buttonRow(JButton... buttons) {
        JPanel p = new JPanel(new GridLayout(1, buttons.length, 8, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(390, 42));
        for (JButton b : buttons) p.add(b);
        return p;
    }

    JButton button(String text, java.awt.event.ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        b.setBorder(new EmptyBorder(9, 10, 9, 10));
        return b;
    }

    void applyTheme() {
        if (theme == null) theme = Theme.all()[0];
        root.setBackground(theme.bg);
        header.setTheme(theme);
        applyThemeRecursive(root);
        for (StatusCard card : new StatusCard[]{turnCard, evalCard, materialCard, bookCard, eloCard, fenCard}) {
            if (card != null) card.setTheme(theme);
        }
        repaint();
    }

    void applyThemeRecursive(Component c) {
        if (c == null) return;
        if (c instanceof JPanel || c instanceof JTabbedPane || c instanceof JScrollPane || c instanceof JViewport) c.setBackground(theme.bg);
        if (c instanceof JLabel) c.setForeground(theme.fg);
        if (c instanceof JCheckBox) {
            c.setBackground(theme.bg);
            c.setForeground(theme.fg);
        }
        if (c instanceof JButton) {
            c.setBackground(UiColors.blend(theme.panel, theme.selected, 0.05));
            c.setForeground(theme.fg);
        }
        if (c instanceof JComboBox) {
            c.setBackground(theme.panel);
            c.setForeground(theme.fg);
        }
        if (c instanceof JTextArea) {
            c.setBackground(theme.panel);
            c.setForeground(theme.fg);
            ((JTextArea)c).setCaretColor(theme.fg);
        }
        if (c instanceof JList) {
            c.setBackground(theme.panel);
            c.setForeground(theme.fg);
        }
        if (c instanceof Container) {
            for (Component child : ((Container)c).getComponents()) applyThemeRecursive(child);
        }
    }

    void newGame() {
        board = Board.starting();
        baseFen = board.toFen();
        redoStack.clear();
        selected = -1;
        hintFrom = -1;
        hintTo = -1;
        puzzleActive = false;
        analysisArea.setText("New game started. Use Analyze for engine lines, Hint for a suggested move, or Review after the game.\n");
        reviewArea.setText("");
        toolsArea.setText("Opening book loaded with " + OpeningBook.LINES.size() + " training lines.\n");
        refresh();
        maybeAiMove();
    }

    boolean currentSideIsHuman() {
        GameMode mode = (GameMode)modeBox.getSelectedItem();
        if (mode == GameMode.HUMAN_VS_HUMAN) return true;
        if (mode == GameMode.HUMAN_VS_AI) return board.turn == Side.WHITE;
        if (mode == GameMode.AI_VS_HUMAN) return board.turn == Side.BLACK;
        return false;
    }

    void squareClicked(int idx) {
        if (aiThinking || board.isGameOver() || !currentSideIsHuman()) return;
        int p = board.sq[idx];
        if (selected < 0) {
            if (p != 0 && Integer.signum(p) == board.turn.sign) selected = idx;
            refresh();
            return;
        }
        if (selected == idx) {
            selected = -1;
            refresh();
            return;
        }
        if (p != 0 && Integer.signum(p) == board.turn.sign) {
            selected = idx;
            refresh();
            return;
        }
        Move candidate = buildMoveFromSelection(selected, idx);
        Move legal = board.findMatchingLegal(candidate);
        if (legal != null) {
            board.makeMove(legal);
            redoStack.clear();
            selected = -1;
            hintFrom = -1;
            hintTo = -1;
            afterMove(legal);
        } else {
            selected = -1;
            refresh();
        }
    }

    Move buildMoveFromSelection(int from, int to) {
        int p = board.sq[from];
        int promo = 0;
        if (Piece.type(p) == Piece.PAWN && (Board.row(to) == 0 || Board.row(to) == 7)) {
            promo = askPromotionPiece();
        }
        return new Move(from, to, promo);
    }

    int askPromotionPiece() {
        String[] choices = {"Queen", "Rook", "Bishop", "Knight"};
        String pick = (String)JOptionPane.showInputDialog(this, "Promote to:", "Promotion", JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if ("Rook".equals(pick)) return board.turn.sign * Piece.ROOK;
        if ("Bishop".equals(pick)) return board.turn.sign * Piece.BISHOP;
        if ("Knight".equals(pick)) return board.turn.sign * Piece.KNIGHT;
        return board.turn.sign * Piece.QUEEN;
    }

    void afterMove(Move legal) {
        if (puzzleActive) checkPuzzleMove(legal);
        refresh();
        if (board.isGameOver()) finishGame();
        else maybeAiMove();
    }

    void maybeAiMove() {
        if (board.isGameOver() || currentSideIsHuman()) return;
        aiThinking = true;
        updateStatus("AI thinking for " + board.turn + "...");
        final Board snapshot = board;
        SwingWorker<Move, Void> worker = new SwingWorker<Move, Void>() {
            protected Move doInBackground() {
                if (bookBox.isSelected()) {
                    Move book = OpeningBook.choose(snapshot);
                    if (book != null) return book;
                }
                AiLevel level = (AiLevel)aiBox.getSelectedItem();
                return ChessAI.chooseMove(snapshot, level);
            }
            protected void done() {
                try {
                    Move m = get();
                    if (m != null && !board.isGameOver()) {
                        Move legal = board.findMatchingLegal(m);
                        if (legal != null) board.makeMove(legal);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ChessFrame.this, "AI error:\n" + ex.getMessage(), "AI error", JOptionPane.ERROR_MESSAGE);
                }
                aiThinking = false;
                refresh();
                if (board.isGameOver()) finishGame();
                else if (!currentSideIsHuman()) maybeAiMove();
            }
        };
        worker.execute();
    }

    void updateStatus(String text) {
        header.right.setText(text);
    }

    Set<Integer> legalTargets() {
        Set<Integer> set = new HashSet<>();
        if (selected >= 0 && highlightBox.isSelected()) for (Move m : board.legalMoves()) if (m.from == selected) set.add(m.to);
        return set;
    }

    Set<Integer> legalCaptures() {
        Set<Integer> set = new HashSet<>();
        if (selected >= 0 && highlightBox.isSelected()) for (Move m : board.legalMoves()) if (m.from == selected && (board.sq[m.to] != 0 || m.enPassant)) set.add(m.to);
        return set;
    }

    void undoPly() {
        if (aiThinking || board.history.isEmpty()) return;
        List<Move> moves = new ArrayList<>(board.history);
        Move undone = moves.remove(moves.size() - 1);
        redoStack.push(undone);
        replay(moves);
    }

    void redoPly() {
        if (aiThinking || redoStack.isEmpty()) return;
        Move m = redoStack.pop();
        Move legal = board.findMatchingLegal(m);
        if (legal != null) board.makeMove(legal);
        refresh();
        if (board.isGameOver()) finishGame();
    }

    void replay(List<Move> moves) {
        try { board = Board.fromFen(baseFen); }
        catch (Exception ex) { board = Board.starting(); baseFen = board.toFen(); }
        for (Move old : moves) {
            Move legal = board.findMatchingLegal(old);
            if (legal != null) board.makeMove(legal);
        }
        selected = -1;
        hintFrom = -1;
        hintTo = -1;
        refresh();
    }

    void showHint() {
        if (aiThinking || board.isGameOver()) return;
        final Board snap = board.copy();
        analysisArea.setText("Calculating hint...\n");
        SwingWorker<ChessAI.SearchResult, Void> worker = new SwingWorker<ChessAI.SearchResult, Void>() {
            protected ChessAI.SearchResult doInBackground() { return ChessAI.analyze(snap, AiLevel.ANALYSIS_FAST); }
            protected void done() {
                try {
                    ChessAI.SearchResult r = get();
                    if (r.bestMove != null) {
                        hintFrom = r.bestMove.from;
                        hintTo = r.bestMove.to;
                        analysisArea.setText("Hint: " + r.bestMove + " (" + r.bestMove.coordinate() + ")\nEval: " + r.scoreLabel() + "\nLine: " + r.principalVariation + "\n");
                    }
                } catch (Exception ignored) {}
                refresh();
            }
        };
        worker.execute();
    }

    void analyzePosition() {
        if (aiThinking) return;
        final Board snap = board.copy();
        analysisArea.setText("Analyzing with " + AiLevel.ANALYSIS + "...\n");
        tabs.setSelectedIndex(2);
        SwingWorker<ChessAI.SearchResult, Void> worker = new SwingWorker<ChessAI.SearchResult, Void>() {
            protected ChessAI.SearchResult doInBackground() { return ChessAI.analyze(snap, AiLevel.ANALYSIS); }
            protected void done() {
                try {
                    ChessAI.SearchResult r = get();
                    StringBuilder sb = new StringBuilder();
                    sb.append("FEN: ").append(board.toFen()).append("\n");
                    sb.append("Side to move: ").append(board.turn).append("\n");
                    sb.append("Evaluation: ").append(r.scoreLabel()).append("\n");
                    sb.append("Best move: ").append(r.bestMove == null ? "-" : r.bestMove + " (" + r.bestMove.coordinate() + ")").append("\n");
                    sb.append("Depth: ").append(r.searchedDepth).append(" | Nodes: ").append(r.nodes).append("\n");
                    sb.append("Principal variation: ").append(r.principalVariation).append("\n\n");
                    sb.append("Top candidates:\n");
                    int n = 1;
                    for (ChessAI.ScoredMove sm : r.candidates) {
                        if (n > 10) break;
                        sb.append(String.format(Locale.US, "%2d. %-8s %-8s %s%n", n++, sm.move.coordinate(), ChessAI.formatEval(sm.scoreWhite), sm.move.toString()));
                    }
                    analysisArea.setText(sb.toString());
                    if (r.bestMove != null) { hintFrom = r.bestMove.from; hintTo = r.bestMove.to; }
                    refresh();
                } catch (Exception ex) {
                    analysisArea.setText("Analysis error: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    void showTopMoves() { analyzePosition(); }

    void playBookMove() {
        if (aiThinking || board.isGameOver()) return;
        Move book = OpeningBook.choose(board);
        if (book == null) {
            JOptionPane.showMessageDialog(this, "No legal book move found for this position.");
            return;
        }
        Move legal = board.findMatchingLegal(book);
        if (legal != null) {
            board.makeMove(legal);
            redoStack.clear();
            afterMove(legal);
        }
    }

    void showReview() {
        if (board.history.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No moves to review yet.");
            return;
        }
        tabs.setSelectedIndex(3);
        reviewArea.setText("Reviewing game. This can take a moment...\n");
        final Board played = board;
        final String startFen = baseFen;
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            protected String doInBackground() { return GameReview.review(played, startFen, AiLevel.ANALYSIS); }
            protected void done() {
                try { reviewArea.setText(get()); }
                catch (Exception ex) { reviewArea.setText("Review error: " + ex.getMessage()); }
            }
        };
        worker.execute();
    }

    void startPuzzle() {
        currentPuzzle = PuzzleBook.randomPuzzle();
        try {
            board = Board.fromFen(currentPuzzle.fen);
            baseFen = board.toFen();
            redoStack.clear();
            selected = -1;
            puzzleActive = true;
            toolsArea.setText("Puzzle: " + currentPuzzle.title + "\nFind the best move for " + board.turn + ".\n");
            tabs.setSelectedIndex(4);
            refresh();
        } catch (Exception ex) {
            toolsArea.setText("Puzzle could not be loaded: " + ex.getMessage());
        }
    }

    void checkPuzzleMove(Move move) {
        if (currentPuzzle == null) return;
        if (move.coordinate().equals(currentPuzzle.solution)) {
            toolsArea.append("\nCorrect: " + move.coordinate() + "\n" + currentPuzzle.explanation + "\n");
            puzzleActive = false;
        } else {
            toolsArea.append("\nPlayed: " + move.coordinate() + "\nTry to find: " + currentPuzzle.explanation + "\n");
        }
    }

    void showPuzzleSolution() {
        if (currentPuzzle == null) {
            JOptionPane.showMessageDialog(this, "Start a puzzle first.");
            return;
        }
        toolsArea.append("\nSolution: " + currentPuzzle.solution + "\n" + currentPuzzle.explanation + "\n");
    }

    void listLegalMoves() {
        StringBuilder sb = new StringBuilder();
        sb.append("Legal moves for ").append(board.turn).append(":\n");
        int i = 1;
        for (Move m : board.legalMoves()) sb.append(String.format(Locale.US, "%2d. %-8s %s%n", i++, m.coordinate(), m.toString()));
        toolsArea.setText(sb.toString());
        tabs.setSelectedIndex(4);
    }

    void runPerft(int depth) {
        tabs.setSelectedIndex(4);
        toolsArea.setText("Running perft depth " + depth + "...\n");
        final Board snap = board.copy();
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            protected String doInBackground() { return Perft.divide(snap, depth); }
            protected void done() {
                try { toolsArea.setText(get()); }
                catch (Exception ex) { toolsArea.setText("Perft error: " + ex.getMessage()); }
            }
        };
        worker.execute();
    }

    void copyAnalysis() {
        String text = analysisArea.getText();
        if (text == null || text.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "There is no analysis text to copy yet.");
            return;
        }
        copyToClipboard(text);
        JOptionPane.showMessageDialog(this, "Analysis copied.");
    }

    void copyLegalMoves() {
        StringBuilder sb = new StringBuilder();
        sb.append("Legal moves for ").append(board.turn).append(":\n");
        int i = 1;
        for (Move m : board.legalMoves()) sb.append(String.format(Locale.US, "%2d. %-8s %s%n", i++, m.coordinate(), m.toString()));
        copyToClipboard(sb.toString());
        toolsArea.setText(sb.toString());
        tabs.setSelectedIndex(4);
    }

    void resetElo() {
        elo.reset();
        refresh();
        JOptionPane.showMessageDialog(this, "Elo ratings reset to 1200.");
    }

    void showCoachAdvice() {
        StringBuilder sb = new StringBuilder();
        sb.append("Coach advice for ").append(board.turn).append("\n");
        sb.append("FEN: ").append(board.toFen()).append("\n\n");

        int eval = ChessAI.evaluate(board);
        sb.append("Static evaluation: ").append(ChessAI.formatEval(eval)).append(" (positive = White better)\n");
        if (board.isInCheck(board.turn)) sb.append("- Your king is in check: first solve the check legally.\n");

        List<Move> legal = board.legalMoves();
        List<Move> checks = new ArrayList<>();
        List<Move> captures = new ArrayList<>();
        List<Move> promotions = new ArrayList<>();
        for (Move m : legal) {
            Board next = board.copy();
            next.makeMoveNoHistory(m);
            if (next.isInCheck(next.turn)) checks.add(m);
            if (board.sq[m.to] != 0 || m.enPassant) captures.add(m);
            if (m.promotion != 0) promotions.add(m);
        }

        sb.append("- Legal moves: ").append(legal.size()).append("\n");
        sb.append("- Checking moves: ").append(checks.size()).append(checks.isEmpty() ? "" : " -> " + movePreview(checks, 6)).append("\n");
        sb.append("- Captures: ").append(captures.size()).append(captures.isEmpty() ? "" : " -> " + movePreview(captures, 6)).append("\n");
        if (!promotions.isEmpty()) sb.append("- Promotions available: ").append(movePreview(promotions, 6)).append("\n");

        if (legal.size() <= 12) sb.append("- Low mobility: look for piece activity and king safety.\n");
        if (ChessAI.nonKingMaterial(board) <= 2400) sb.append("- Endgame phase: activate the king and push passed pawns.\n");
        else sb.append("- Middlegame/opening phase: improve the worst placed piece before starting risky attacks.\n");

        ChessAI.SearchResult quick = ChessAI.analyze(board, AiLevel.ANALYSIS_FAST);
        if (quick.bestMove != null) {
            sb.append("\nEngine suggestion: ").append(quick.bestMove).append(" (").append(quick.bestMove.coordinate()).append(")\n");
            sb.append("Line: ").append(quick.principalVariation).append("\n");
        }

        analysisArea.setText(sb.toString());
        tabs.setSelectedIndex(2);
    }

    String movePreview(List<Move> moves, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moves.size() && i < max; i++) {
            if (i > 0) sb.append(", ");
            sb.append(moves.get(i));
        }
        if (moves.size() > max) sb.append(", ...");
        return sb.toString();
    }

    void copyFen() {
        copyToClipboard(board.toFen());
        JOptionPane.showMessageDialog(this, "FEN copied.\n" + board.toFen());
    }

    void loadFen() {
        if (aiThinking) return;
        String in = JOptionPane.showInputDialog(this, "Paste FEN:", board.toFen());
        if (in == null || in.trim().isEmpty()) return;
        try {
            board = Board.fromFen(in.trim());
            baseFen = board.toFen();
            redoStack.clear();
            selected = -1;
            puzzleActive = false;
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid FEN:\n" + ex.getMessage(), "FEN error", JOptionPane.ERROR_MESSAGE);
        }
    }

    String pgnText() {
        String result = board.resultString().equals("*") ? "*" : board.resultString().split(" ")[0];
        StringBuilder sb = new StringBuilder();
        sb.append("[Event \"ChessPro Ultimate local game\"]\n");
        sb.append("[Site \"Java Swing\"]\n");
        sb.append("[Result \"").append(result).append("\"]\n");
        sb.append("[FEN \"").append(baseFen).append("\"]\n");
        sb.append("[Opening \"").append(OpeningBook.currentName(board)).append("\"]\n\n");
        for (int i = 0; i < board.history.size(); i++) {
            if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
            sb.append(board.history.get(i)).append(' ');
        }
        sb.append(result).append('\n');
        return sb.toString();
    }

    void copyPgn() {
        copyToClipboard(pgnText());
        JOptionPane.showMessageDialog(this, "PGN copied to clipboard.");
    }

    void exportPgn() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("chesspro_game.pgn"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.write(chooser.getSelectedFile().toPath(), pgnText().getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(this, "Saved:\n" + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save PGN:\n" + ex.getMessage(), "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    void saveReview() {
        if (board.history.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No moves to review yet.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("chesspro_review.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String text = GameReview.review(board, baseFen, AiLevel.ANALYSIS);
            Files.write(chooser.getSelectedFile().toPath(), text.getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(this, "Saved:\n" + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save review:\n" + ex.getMessage(), "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    void saveBoardImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("chesspro_board.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(boardCanvas.getWidth(), boardCanvas.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            boardCanvas.paint(g2);
            g2.dispose();
            javax.imageio.ImageIO.write(img, "png", chooser.getSelectedFile());
            JOptionPane.showMessageDialog(this, "Board image saved.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save image:\n" + ex.getMessage(), "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    void copyAsciiBoard() {
        String ascii = asciiBoard();
        copyToClipboard(ascii);
        toolsArea.setText(ascii);
        tabs.setSelectedIndex(4);
    }

    String asciiBoard() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            sb.append(8 - r).append("  ");
            for (int c = 0; c < 8; c++) {
                int p = board.sq[Board.idx(r, c)];
                sb.append(p == 0 ? "." : String.valueOf(Piece.fenChar(p))).append(' ');
            }
            sb.append('\n');
        }
        sb.append("\n   a b c d e f g h\n");
        sb.append("FEN: ").append(board.toFen()).append('\n');
        return sb.toString();
    }

    void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    void finishGame() {
        String result = board.resultString();
        updateStatus(result);
        GameMode mode = (GameMode)modeBox.getSelectedItem();
        if (mode == GameMode.HUMAN_VS_AI || mode == GameMode.AI_VS_HUMAN) {
            double whiteScore = board.scoreForWhite();
            double humanScore = mode == GameMode.HUMAN_VS_AI ? whiteScore : 1.0 - whiteScore;
            elo.update(humanScore);
        }
        refresh();
        JOptionPane.showMessageDialog(this, result + "\n" + elo.label(), "Game over", JOptionPane.INFORMATION_MESSAGE);
    }

    void refresh() {
        rebuildMoveList();
        if (board.isGameOver()) updateStatus(board.resultString());
        else updateStatus("Turn: " + board.turn + (board.isInCheck(board.turn) ? " - check" : ""));
        if (turnCard != null) turnCard.setValue(board.turn + (board.isInCheck(board.turn) ? " + check" : ""));
        if (evalCard != null) evalCard.setValue(autoAnalyzeBox.isSelected() ? ChessAI.formatEval(ChessAI.evaluate(board)) : "off");
        if (materialCard != null) materialCard.setValue(board.materialLabel().replace("Material: ", ""));
        if (bookCard != null) bookCard.setValue(shorten(OpeningBook.currentName(board), 28));
        if (eloCard != null) eloCard.setValue(elo.label());
        if (fenCard != null) fenCard.setValue(shorten(board.toFen(), 32));
        boardCanvas.repaint();
    }

    void rebuildMoveList() {
        moveListModel.clear();
        String line = "";
        for (int i = 0; i < board.history.size(); i++) {
            Move m = board.history.get(i);
            if (i % 2 == 0) line = String.format(Locale.US, "%3d. %-10s", i / 2 + 1, m.toString());
            else {
                line += String.format(Locale.US, " %-10s", m.toString());
                moveListModel.addElement(line);
                line = "";
            }
        }
        if (!line.isEmpty()) moveListModel.addElement(line);
        if (moveListModel.size() > 0) moveList.ensureIndexIsVisible(moveListModel.size() - 1);
    }

    String shorten(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }
}

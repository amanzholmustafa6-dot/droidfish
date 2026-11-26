/*
    DroidFish - An Android chess program.
    Copyright (C) 2011-2012  Peter Österlund, peterosterlund2@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.petero.droidfish.gamelogic;

import android.util.Pair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.petero.droidfish.EngineOptions;
import org.petero.droidfish.GUIInterface;
import org.petero.droidfish.GUIInterface.ThinkingInfo;
import org.petero.droidfish.GameMode;
import org.petero.droidfish.PGNOptions;
import org.petero.droidfish.Util;
import org.petero.droidfish.book.BookOptions;
import org.petero.droidfish.book.EcoDb;
import org.petero.droidfish.book.IOpeningBook.BookPosInput;
import org.petero.droidfish.engine.DroidComputerPlayer;
import org.petero.droidfish.engine.UCIOptions;
import org.petero.droidfish.engine.DroidComputerPlayer.EloData;
import org.petero.droidfish.engine.DroidComputerPlayer.SearchRequest;
import org.petero.droidfish.engine.DroidComputerPlayer.SearchType;
import org.petero.droidfish.gamelogic.Game.CommentInfo;
import org.petero.droidfish.gamelogic.Game.GameState;
import org.petero.droidfish.gamelogic.GameTree.Node;

/** The glue between the chess engine and the GUI. */
public class DroidChessController {
    private DroidComputerPlayer computerPlayer = null;
    private PgnToken.PgnTokenReceiver gameTextListener;
    private BookOptions bookOptions = new BookOptions();
    private EngineOptions engineOptions = new EngineOptions();
    private Game game = null;
    private Move ponderMove = null;
    private GUIInterface gui;
    private GameMode gameMode;
    private PGNOptions pgnOptions;

    private String engine = "";
    private int numPV = 1;

    private SearchListener listener;
    private boolean guiPaused = false;

    /** Partial move that needs promotion choice to be completed. */
    private Move promoteMove;

    private int searchId;
    private volatile ThinkingInfo latestThinkingInfo = null;

    /** Constructor. */
    public DroidChessController(GUIInterface gui, PgnToken.PgnTokenReceiver gameTextListener, PGNOptions options) {
        this.gui = gui;
        this.gameTextListener = gameTextListener;
        gameMode = new GameMode(GameMode.TWO_PLAYERS);
        pgnOptions = options;
        listener = new SearchListener();
        searchId = 0;
    }

    /** Start a new game. */
    public final synchronized void newGame(GameMode gameMode, TimeControlData tcData) {
        boolean updateGui = abortSearch();
        if (updateGui)
            updateGUI();
        this.gameMode = gameMode;
        if (computerPlayer == null) {
            computerPlayer = new DroidComputerPlayer(listener);
            computerPlayer.setBookOptions(bookOptions);
            computerPlayer.setEngineOptions(engineOptions);
        }
        computerPlayer.queueStartEngine(searchId, engine);
        searchId++;
        Game oldGame = game;
        game = new Game(gameTextListener, tcData);
        computerPlayer.uciNewGame();
        setPlayerNames(game);
        updateGameMode();
        game.resetModified(pgnOptions);
        autoSaveOldGame(oldGame, game.treeHashSignature);
    }

    /** Save old game if has been modified since start/load of game and is
     *  not equal to the new game. */
    private void autoSaveOldGame(Game oldGame, long newGameHash) {
        if (oldGame == null)
            return;
        String pgn = oldGame.tree.toPGN(pgnOptions);
        long oldGameOrigHash = oldGame.treeHashSignature;
        long oldGameCurrHash = Util.stringHash(pgn);
        if (oldGameCurrHash != oldGameOrigHash && oldGameCurrHash != newGameHash)
            gui.autoSaveGameIfAllowed(pgn);
    }

    /** Start playing a new game. Should be called after newGame(). */
    public final synchronized void startGame() {
        updateComputeThreads();
        setSelection();
        updateGUI();
        updateGameMode();
    }

    /** @return Array containing time control, moves per session and time increment. */
    public final int[] getTimeLimit() {
        if (game != null)
            return game.timeController.getTimeLimit(game.currPos().whiteMove);
        return new int[]{5*60*1000, 60, 0};
    }

    /** The chess clocks are stopped when the GUI is paused. */
    public final synchronized void setGuiPaused(boolean paused) {
        guiPaused = paused;
        updateGameMode();
    }

    /** Set game mode. */
    public final synchronized void setGameMode(GameMode newMode) {
        if (!gameMode.equals(newMode)) {
            if (newMode.humansTurn(game.currPos().whiteMove))
                searchId++;
            gameMode = newMode;
            if (!gameMode.playerWhite() || !gameMode.playerBlack())
                setPlayerNames(game); // If computer player involved, set player names
            updateGameMode();
            gui.updateEngineTitle(getEloToUse()); // Game mode affects Elo setting
            restartSearch();
        }
    }

    private int getEloToUse() {
        return eloData().getEloToUse();
    }

    public final GameMode getGameMode() {
        return gameMode;
    }

    /** Return true if game mode is analysis. */
    public final boolean analysisMode() {
        return gameMode.analysisMode();
    }

    /** Set engine book options. */
    public final synchronized void setBookOptions(BookOptions options) {
        if (!bookOptions.equals(options)) {
            bookOptions = options;
            if (computerPlayer != null) {
                computerPlayer.setBookOptions(bookOptions);
                updateBookHints();
            }
        }
    }

    /** Set engine options. */
    public final synchronized void setEngineOptions(EngineOptions options, boolean restart) {
        if (!engineOptions.equals(options)) {
            engineOptions = options;
            if (computerPlayer != null)
                computerPlayer.setEngineOptions(engineOptions);
            if (restart)
                restartSearch();
        }
    }

    private void restartSearch() {
        if (game != null) {
            abortSearch();
            updateComputeThreads();
            updateGUI();
        }
    }

    /** Set engine. Restart computer thinking if appropriate. */
    public final synchronized void setEngine(String engine) {
        if (!engine.equals(this.engine)) {
            this.engine = engine;
            restartSearch();
        }
    }

    /** Set engine strength. Restart computer thinking if appropriate. */
    public final synchronized void setStrength(boolean limitStrength, int elo) {
        EloData d = eloData();
        int oldElo = d.getEloToUse();
        d.limitStrength = limitStrength;
        d.elo = elo;
        int newElo = d.getEloToUse();
        if (oldElo != newElo) {
            if (computerPlayer != null)
                computerPlayer.setStrength(newElo);
            restartSearch();
            gui.updateEngineTitle(newElo);
        }
    }

    /** Return engine Elo strength data. */
    public final synchronized EloData eloData() {
        if (computerPlayer == null)
            return new EloData();
        return computerPlayer.getEloData();
    }

    /** Set engine UCI options. */
    public final synchronized void setEngineUCIOptions(Map<String,String> uciOptions) {
        if (computerPlayer != null)
            computerPlayer.setEngineUCIOptions(uciOptions);
    }

    /** Return current engine identifier. */
    public final synchronized String getEngine() {
        return engine;
    }

    /** Notify controller that preferences has changed. */
    public final synchronized void prefsChanged(boolean translateMoves) {
        if (game == null)
            translateMoves = false;
        if (translateMoves)
            game.tree.translateMoves();
        updateBookHints();
        updateMoveList();
        listener.prefsChanged(searchId, translateMoves);
        if (translateMoves)
            updateGUI();
    }

    /** De-serialize from byte array. */
    public final synchronized void fromByteArray(byte[] data, int version) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            game.readFromStream(dis, version);
            game.tree.translateMoves();
        } catch (IOException|ChessParseError ignore) {
        }
    }

    /** Serialize to byte array. */
    public final synchronized byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
             DataOutputStream dos = new DataOutputStream(baos)) {
            game.writeToStream(dos);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** Return FEN string corresponding to a current position. */
    public final synchronized String getFEN() {
        return TextIO.toFEN(game.tree.currentPos);
    }

    /** Convert current game to PGN format. */
    public final synchronized String getPGN() {
        return game.tree.toPGN(pgnOptions);
    }

    /** Parse a string as FEN or PGN data. */
    public final synchronized void setFENOrPGN(String fenPgn, boolean setModified) throws ChessParseError {
        if (!fenPgn.isEmpty() && fenPgn.charAt(0) == '\ufeff')
            fenPgn = fenPgn.substring(1); // Remove BOM
        Game newGame = new Game(gameTextListener, game.timeController.tcData);
        try {
            Position pos = TextIO.readFEN(fenPgn);
            newGame.setPos(pos);
            setPlayerNames(newGame);
        } catch (ChessParseError e) {
            // Try read as PGN instead
            if (!newGame.readPGN(fenPgn, pgnOptions))
                throw e;
            newGame.tree.translateMoves();
        }
        searchId++;
        Game oldGame = game;
        game = newGame;
        gameTextListener.clear();
        updateGameMode();
        abortSearch();
        computerPlayer.uciNewGame();
        updateComputeThreads();
        gui.setSelection(-1);
        updateGUI();
        game.resetModified(pgnOptions);
        autoSaveOldGame(oldGame, game.treeHashSignature);
        if (setModified)
            game.treeHashSignature = oldGame.treeHashSignature;
    }

    public final synchronized void setLastSaveHash(long hash) {
        game.treeHashSignature = hash;
    }

    /** True if human's turn to make a move. (True in analysis mode.) */
    public final synchronized boolean humansTurn() {
        if (game == null)
            return false;
        return gameMode.humansTurn(game.currPos().whiteMove);
    }

    /** Return true if computer player is using CPU power. */
    public final synchronized boolean computerBusy() {
        return (computerPlayer != null) && computerPlayer.computerBusy();
    }

    /** Return engine UCI options if an engine has been loaded and has
     *  reported its UCI options. */
    public final synchronized UCIOptions getUCIOptions() {
        if (computerPlayer == null || !computerPlayer.computerLoaded())
            return null;
        return computerPlayer.getUCIOptions();
    }

    /** Make a move for a human player. */
    public final synchronized void makeHumanMove(Move m, boolean animate) {
        if (!humansTurn())
            return;
        Position oldPos = new Position(game.currPos());
        if (game.pendingDrawOffer) {
            ArrayList<Move> moves = new MoveGen().legalMoves(oldPos);
            for (Move m2 : moves) {
                if (m2.equals(m)) {
                    if (findValidDrawClaim(TextIO.moveToUCIString(m))) {
                        stopPonder();
                        updateGUI();
                        gui.setSelection(-1);
                        return;
                    }
                    break;
                }
            }
        }
        if (doMove(m)) {
            if (m.equals(ponderMove) && !gameMode.analysisMode() &&
                    (computerPlayer.getSearchType() == SearchType.PONDER)) {
                computerPlayer.ponderHit(searchId);
                ponderMove = null;
            } else {
                abortSearch();
                updateComputeThreads();
            }
            if (animate)
                setAnimMove(oldPos, m, true);
            updateGUI();
        } else {
            gui.setSelection(-1);
        }
    }

    /** Report promotion choice for incomplete move.
     * @param choice 0=queen, 1=rook, 2=bishop, 3=knight. */
    public final synchronized void reportPromotePiece(int choice) {
        if (promoteMove == null)
            return;
        final boolean white = game.currPos().whiteMove;
        int promoteTo;
        switch (choice) {
            case 1:
                promoteTo = white ? Piece.WROOK : Piece.BROOK;
                break;
            case 2:
                promoteTo = white ? Piece.WBISHOP : Piece.BBISHOP;
                break;
            case 3:
                promoteTo = white ? Piece.WKNIGHT : Piece.BKNIGHT;
                break;
            default:
                promoteTo = white ? Piece.WQUEEN : Piece.BQUEEN;
                break;
        }
        promoteMove.promoteTo = promoteTo;
        Move m = promoteMove;
        promoteMove = null;
        makeHumanMove(m, true);
    }

    /** Add a null-move to the game tree. */
    public final synchronized void makeHumanNullMove() {
        if (humansTurn()) {
            int varNo = game.tree.addMove("--", "", 0, "", "");
            game.tree.goForward(varNo);
            restartSearch();
            gui.setSelection(-1);
        }
    }

    /** Help human to claim a draw by trying to find and execute a valid draw claim. */
    public final synchronized boolean claimDrawIfPossible() {
        if (!findValidDrawClaim(""))
            return false;
        updateGUI();
        return true;
    }

    /** Resign game for current player. */
    public final synchronized void resignGame() {
        if (game.getGameState() == GameState.ALIVE) {
            game.processString("resign");
            updateGUI();
        }
    }

    /** Return true if the player to move in the current position is in check. */
    public final synchronized boolean inCheck() {
        return MoveGen.inCheck(game.tree.currentPos);
    }

    /** Undo last move. Does not truncate game tree. */
    public final synchronized void undoMove() {
        if (game.getLastMove() != null) {
            abortSearch();
            boolean didUndo = undoMoveNoUpdate();
            updateComputeThreads();
            setSelection();
            if (didUndo)
                setAnimMove(game.currPos(), game.getNextMove(), false);
            updateGUI();
        }
    }

    /** Redo last move. Follows default variation. */
    public final synchronized void redoMove() {
        if (game.canRedoMove()) {
            abortSearch();
            redoMoveNoUpdate();
            updateComputeThreads();
            setSelection();
            setAnimMove(game.prevPos(), game.getLastMove(), true);
            updateGUI();
        }
    }

    /** Go back/forward to a given move number.
     * Follows default variations when going forward. */
    public final synchronized void gotoMove(int moveNr) {
        boolean needUpdate = false;
        while (game.currPos().fullMoveCounter > moveNr) { // Go backward
            int before = game.currPos().fullMoveCounter * 2 + (game.currPos().whiteMove ? 0 : 1);
            undoMoveNoUpdate();
            int after = game.currPos().fullMoveCounter * 2 + (game.currPos().whiteMove ? 0 : 1);
            if (after >= before)
                break;
            needUpdate = true;
        }
        while (game.currPos().fullMoveCounter < moveNr) { // Go forward
            int before = game.currPos().fullMoveCounter * 2 + (game.currPos().whiteMove ? 0 : 1);
            redoMoveNoUpdate();
            int after = game.currPos().fullMoveCounter * 2 + (game.currPos().whiteMove ? 0 : 1);
            if (after <= before)
                break;
            needUpdate = true;
        }
        if (needUpdate) {
            abortSearch();
            updateComputeThreads();
            setSelection();
            updateGUI();
        }
    }

    /** Go to start of the current variation. */
    public final synchronized void gotoStartOfVariation() {
        boolean needUpdate = false;
        while (true) {
            if (!undoMoveNoUpdate())
                break;
            needUpdate = true;
            if (game.numVariations() > 1)
                break;
        }
        if (needUpdate) {
            abortSearch();
            updateComputeThreads();
            setSelection();
            updateGUI();
        }
    }

    /** Go to given node in game tree. */
    public final synchronized void goNode(Node node) {
        if (node == null)
            return;
        if (!game.goNode(node))
            return;
        if (!humansTurn()) {
            if (game.getLastMove() != null) {
                game.undoMove();
                if (!humansTurn())
                    game.redoMove();
            }
        }
        abortSearch();
        updateComputeThreads();
        setSelection();
        updateGUI();
    }

    /** Get number of variations in current game position. */
    public final synchronized int numVariations() {
        return game.numVariations();
    }

    /** Return true if the current variation can be moved closer to the main-line. */
    public final synchronized boolean canMoveVariationUp() {
        return game.canMoveVariation(-1);
    }

    /** Return true if the current variation can be moved farther away from the main-line. */
    public final synchronized boolean canMoveVariationDown() {
        return game.canMoveVariation(1);
    }

    /** Get current variation in current position. */
    public final synchronized int currVariation() {
        return game.currVariation();
    }

    /** Go to a new variation in the game tree. */
    public final synchronized void changeVariation(int delta) {
        if (game.numVariations() > 1) {
            abortSearch();
            game.changeVariation(delta);
            updateComputeThreads();
            setSelection();
            updateGUI();
        }
    }

    /** Delete whole game sub-tree rooted at current position. */
    public final synchronized void removeSubTree() {
        abortSearch();
        game.removeSubTree();
        updateComputeThreads();
        setSelection();
        updateGUI();
    }

    /** Move current variation up/down in the game tree. */
    public final synchronized void moveVariation(int delta) {
        if (((delta > 0) && canMoveVariationDown()) ||
            ((delta < 0) && canMoveVariationUp())) {
            game.moveVariation(delta);
            updateGUI();
        }
    }

    /** Add a variation to the game tree.
     * @param preComment Comment to add before first move.
     * @param pvMoves List of moves in variation.
     * @param updateDefault If true, make this the default variation. */
    public final synchronized void addVariation(String preComment, List<Move> pvMo

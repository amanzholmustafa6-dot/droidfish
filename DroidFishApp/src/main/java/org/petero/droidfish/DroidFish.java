/*
    DroidFish - An Android chess program.
    Copyright (C) 2011-2014  Peter Österlund, peterosterlund2@gmail.com
    Copyright (C) 2012 Leo Mayer

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

package org.petero.droidfish;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.petero.droidfish.activities.CPUWarning;
import org.petero.droidfish.activities.EditBoard;
import org.petero.droidfish.activities.EditOptions;
import org.petero.droidfish.activities.EditPGNLoad;
import org.petero.droidfish.activities.EditPGNSave;
import org.petero.droidfish.activities.LoadFEN;
import org.petero.droidfish.activities.LoadScid;
import org.petero.droidfish.activities.util.PGNFile;
import org.petero.droidfish.activities.util.PGNFile.GameInfo;
import org.petero.droidfish.activities.Preferences;
import org.petero.droidfish.book.BookOptions;
import org.petero.droidfish.engine.DroidComputerPlayer.EloData;
import org.petero.droidfish.engine.EngineUtil;
import org.petero.droidfish.engine.UCIOptions;
import org.petero.droidfish.gamelogic.DroidChessController;
import org.petero.droidfish.gamelogic.ChessParseError;
import org.petero.droidfish.gamelogic.Game;
import org.petero.droidfish.gamelogic.Move;
import org.petero.droidfish.gamelogic.Position;
import org.petero.droidfish.gamelogic.TextIO;
import org.petero.droidfish.gamelogic.GameTree.Node;
import org.petero.droidfish.gamelogic.TimeControlData;
import org.petero.droidfish.tb.Probe;
import org.petero.droidfish.tb.ProbeResult;
import org.petero.droidfish.view.MoveListView;
import org.petero.droidfish.view.ChessBoard.SquareDecoration;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import tourguide.tourguide.Overlay;
import tourguide.tourguide.Pointer;
import tourguide.tourguide.Sequence;
import tourguide.tourguide.ToolTip;
import tourguide.tourguide.TourGuide;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.kalab.chess.enginesupport.ChessEngine;
import com.kalab.chess.enginesupport.ChessEngineResolver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("ClickableViewAccessibility")
public class DroidFish extends Activity
                       implements GUIInterface,
                                  ActivityCompat.OnRequestPermissionsResultCallback {
    private ChessBoardPlay cb;
    DroidChessController ctrl = null;
    private boolean mShowThinking;
    private boolean mShowStats;
    private boolean fullPVLines;
    private int numPV;
    private boolean mWhiteBasedScores;
    private boolean mShowBookHints;
    private int mEcoHints;
    private int maxNumArrows;
    GameMode gameMode;
    private boolean mPonderMode;
    private int timeControl;
    private int movesPerSession;
    private int timeIncrement;
    private String playerName;
    private boolean boardFlipped;
    private boolean autoSwapSides;
    private boolean playerNameFlip;
    private boolean discardVariations;

    private TextView status;
    private ScrollView moveListScroll;
    private MoveListView moveList;
    private View thinkingScroll;
    private TextView thinking;
    private View buttons;
    private ImageButton custom1Button, custom2Button, custom3Button;
    private ImageButton modeButton, undoButton, redoButton;
    private ButtonActions custom1ButtonActions, custom2ButtonActions, custom3ButtonActions;
    private TextView whiteTitleText, blackTitleText, engineTitleText;
    private View secondTitleLine;
    private TextView whiteFigText, blackFigText, summaryTitleText;
    private Dialog moveListMenuDlg;

    private DrawerLayout drawerLayout;
    private ListView leftDrawer;
    private ListView rightDrawer;

    private SharedPreferences settings;
    private ObjectCache cache;

    boolean dragMoveEnabled;
    float scrollSensitivity;
    boolean invertScrollDirection;
    boolean scrollGames;
    private boolean autoScrollMoveList;

    private boolean leftHanded;
    private String moveAnnounceType;
    private boolean moveSoundEnabled;
    private MediaPlayer moveSound;
    private boolean vibrateEnabled;
    private boolean animateMoves;
    private boolean autoScrollTitle;
    private boolean showVariationLine;

    private int autoMoveDelay; // Delay in auto forward/backward mode
    enum AutoMode {
        OFF, FORWARD, BACKWARD
    }
    private AutoMode autoMode = AutoMode.OFF;

    private int ECO_HINTS_OFF = 0;
    private int ECO_HINTS_AUTO = 1;
    private int ECO_HINTS_ALWAYS = 2;

    /** State of requested permissions. */
    private enum PermissionState {
        UNKNOWN,
        REQUESTED,
        GRANTED,
        DENIED
    }
    /** State of WRITE_EXTERNAL_STORAGE permission. */
    private PermissionState storagePermission = PermissionState.UNKNOWN;

    private static String bookDir = "DroidFish/book";
    private static String pgnDir = "DroidFish/pgn";
    private static String fenDir = "DroidFish/epd";
    private static String engineDir = "DroidFish/uci";
    private static String engineLogDir = "DroidFish/uci/logs";
    private static String gtbDefaultDir = "DroidFish/gtb";
    private static String rtbDefaultDir = "DroidFish/rtb";
    private BookOptions bookOptions = new BookOptions();
    private PGNOptions pgnOptions = new PGNOptions();
    private EngineOptions engineOptions = new EngineOptions();

    private long lastVisibleMillis; // Time when GUI became invisible. 0 if currently visible.
    private long lastComputationMillis; // Time when engine last showed that it was computing.

    private PgnScreenText gameTextListener;

    private Typeface figNotation;
    private Typeface defaultThinkingListTypeFace;

    private boolean guideShowOnStart;
    private TourGuide tourGuide;

    private Speech speech;


    /** Defines all configurable button actions. */
    ActionFactory actionFactory = new ActionFactory() {
        private HashMap<String, UIAction> actions;

        private void addAction(UIAction a) {
            actions.put(a.getId(), a);
        }

        {
            actions = new HashMap<>();
            addAction(new UIAction() {
                public String getId() { return "flipboard"; }
                public int getName() { return R.string.flip_board; }
                public int getIcon() { return R.raw.flip; }
                public boolean enabled() { return true; }
                public void run() {
                    boardFlipped = !cb.flipped;
                    setBooleanPref("boardFlipped", boardFlipped);
                    cb.setFlipped(boardFlipped);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "showThinking"; }
                public int getName() { return R.string.toggle_show_thinking; }
                public int getIcon() { return R.raw.thinking; }
                public boolean enabled() { return true; }
                public void run() {
                    mShowThinking = toggleBooleanPref("showThinking");
                    updateThinkingInfo();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "bookHints"; }
                public int getName() { return R.string.toggle_book_hints; }
                public int getIcon() { return R.raw.book; }
                public boolean enabled() { return true; }
                public void run() {
                    mShowBookHints = toggleBooleanPref("bookHints");
                    updateThinkingInfo();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "tbHints"; }
                public int getName() { return R.string.toggle_tb_hints; }
                public int getIcon() { return R.raw.tb; }
                public boolean enabled() { return true; }
                public void run() {
                    engineOptions.hints = toggleBooleanPref("tbHints");
                    setEgtbHints(cb.getSelectedSquare());
                }
            });
            addAction(new UIAction() {
                public String getId() { return "viewVariations"; }
                public int getName() { return R.string.toggle_pgn_variations; }
                public int getIcon() { return R.raw.variation; }
                public boolean enabled() { return true; }
                public void run() {
                    pgnOptions.view.variations = toggleBooleanPref("viewVariations");
                    gameTextListener.clear();
                    ctrl.prefsChanged(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "viewComments"; }
                public int getName() { return R.string.toggle_pgn_comments; }
                public int getIcon() { return R.raw.comment; }
                public boolean enabled() { return true; }
                public void run() {
                    pgnOptions.view.comments = toggleBooleanPref("viewComments");
                    gameTextListener.clear();
                    ctrl.prefsChanged(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "viewHeaders"; }
                public int getName() { return R.string.toggle_pgn_headers; }
                public int getIcon() { return R.raw.header; }
                public boolean enabled() { return true; }
                public void run() {
                    pgnOptions.view.headers = toggleBooleanPref("viewHeaders");
                    gameTextListener.clear();
                    ctrl.prefsChanged(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "toggleAnalysis"; }
                public int getName() { return R.string.toggle_analysis; }
                public int getIcon() { return R.raw.analyze; }
                public boolean enabled() { return true; }
                private int oldGameModeType = GameMode.EDIT_GAME;
                public void run() {
                    int gameModeType;
                    if (ctrl.analysisMode()) {
                        gameModeType = oldGameModeType;
                    } else {
                        oldGameModeType = ctrl.getGameMode().getModeNr();
                        gameModeType = GameMode.ANALYSIS;
                    }
                    newGameMode(gameModeType);
                    setBoardFlip(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "forceMove"; }
                public int getName() { return R.string.option_force_computer_move; }
                public int getIcon() { return R.raw.stop; }
                public boolean enabled() { return true; }
                public void run() {
                    ctrl.stopSearch();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "largeButtons"; }
                public int getName() { return R.string.toggle_large_buttons; }
                public int getIcon() { return R.raw.magnify; }
                public boolean enabled() { return true; }
                public void run() {
                    toggleBooleanPref("largeButtons");
                    updateButtons();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "blindMode"; }
                public int getName() { return R.string.blind_mode; }
                public int getIcon() { return R.raw.blind; }
                public boolean enabled() { return true; }
                public void run() {
                    boolean blindMode = !cb.blindMode;
                    setBooleanPref("blindMode", blindMode);
                    cb.setBlindMode(blindMode);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "loadLastFile"; }
                public int getName() { return R.string.load_last_file; }
                public int getIcon() { return R.raw.open_last_file; }
                public boolean enabled() { return currFileType() != FT_NONE && storageAvailable(); }
                public void run() {
                    loadLastFile();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "loadGame"; }
                public int getName() { return R.string.load_game; }
                public int getIcon() { return R.raw.open_file; }
                public boolean enabled() { return storageAvailable(); }
                public void run() {
                    selectFile(R.string.select_pgn_file, R.string.pgn_load, "currentPGNFile", pgnDir,
                               SELECT_PGN_FILE_DIALOG, RESULT_OI_PGN_LOAD);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "selectEngine"; }
                public int getName() { return R.string.select_engine; }
                public int getIcon() { return R.raw.engine; }
                public boolean enabled() { return true; }
                public void run() {
                    reShowDialog(SELECT_ENGINE_DIALOG_NOMANAGE);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "engineOptions"; }
                public int getName() { return R.string.engine_options; }
                public int getIcon() { return R.raw.custom; }
                public boolean enabled() { return canSetEngineOptions(); }
                public void run() {
                    setEngineOptions();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "toggleArrows"; }
                public int getName() { return R.string.toggle_arrows; }
                public int getIcon() { return R.raw.custom; }
                public boolean enabled() { return true; }
                public void run() {
                    String numArrows = settings.getString("thinkingArrows", "4");
                    Editor editor = settings.edit();
                    if (!"0".equals(numArrows)) {
                        editor.putString("thinkingArrows", "0");
                        editor.putString("oldThinkingArrows", numArrows);
                    } else {
                        String oldNumArrows = settings.getString("oldThinkingArrows", "0");
                        if ("0".equals(oldNumArrows))
                            oldNumArrows = "4";
                        editor.putString("thinkingArrows", oldNumArrows);
                    }
                    editor.apply();
                    maxNumArrows = getIntSetting("thinkingArrows", 4);
                    updateThinkingInfo();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "prevGame"; }
                public int getName() { return R.string.load_prev_game; }
                public int getIcon() { return R.raw.custom; }
                public boolean enabled() {
                    return (currFileType() != FT_NONE) && !gameMode.clocksActive();
                }
                public void run() {
                    final int currFT = currFileType();
                    final String currPathName = currPathName();
                    Intent i;
                    if (currFT == FT_PGN) {
                        i = new Intent(DroidFish.this, EditPGNLoad.class);
                        i.setAction("org.petero.droidfish.loadFilePrevGame");
                        i.putExtra("org.petero.droidfish.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_SCID) {
                        i = new Intent(DroidFish.this, LoadScid.class);
                        i.setAction("org.petero.droidfish.loadScidPrevGame");
                        i.putExtra("org.petero.droidfish.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_FEN) {
                        i = new Intent(DroidFish.this, LoadFEN.class);
                        i.setAction("org.petero.droidfish.loadPrevFen");
                  

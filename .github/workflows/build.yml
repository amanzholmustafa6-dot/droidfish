package org.petero.droidfish.gamelogic;

/**
 * Оценка качества хода (как в chess.com / lichess).
 * Положить в DroidFish/src/main/java/org/petero/droidfish/gamelogic/
 * Все значения передаются с точки зрения игрока, который сделал ход.
 */
public final class MoveClassifier {
    public enum Quality { BRILLIANT, BEST, GOOD, INACCURACY, MISTAKE, BLUNDER }

    private MoveClassifier() { }

    /** Оценка в сотых пешки -> шанс выигрыша 0..100 (формула lichess). */
    public static double winPercent(int cp) {
        cp = Math.max(-1000, Math.min(1000, cp));
        return 50.0 + 50.0 * (2.0 / (1.0 + Math.exp(-0.00368208 * cp)) - 1.0);
    }

    /** Мат переводим в большое число, чтобы работала та же формула. */
    public static int scoreToCp(int score, boolean isMate) {
        if (!isMate) return score;
        return score > 0 ? 10000 : -10000;
    }

    /**
     * @param cpBestBefore  оценка позиции до хода (лучший ход движка)
     * @param cpAfterPlayed оценка позиции после сыгранного хода
     * @param matBefore     материал (в сотых пешки) до хода: свой минус чужой
     * @param matAfterReply материал после ответа соперника (1-2 полухода PV)
     * @param isBestMove    сыгранный ход совпал с первым ходом движка
     */
    public static Quality classify(int cpBestBefore, int cpAfterPlayed,
                                   int matBefore, int matAfterReply,
                                   boolean isBestMove) {
        double before = winPercent(cpBestBefore);
        double after = winPercent(cpAfterPlayed);
        double loss = Math.max(0.0, before - after);

        // Блестящий: лучший/почти лучший, жертва >= лёгкая фигура,
        // позиция не проиграна и ещё не была полностью выигранной.
        boolean sacrifice = (matBefore - matAfterReply) >= 250;
        if ((isBestMove || loss < 2.0) && sacrifice
                && after >= 45.0 && before < 90.0) {
            return Quality.BRILLIANT;
        }
        if (isBestMove || loss < 2.0) return Quality.BEST;
        if (loss < 5.0) return Quality.GOOD;
        if (loss < 10.0) return Quality.INACCURACY;
        if (loss < 20.0) return Quality.MISTAKE;
        return Quality.BLUNDER;
    }

    /** Стандартные PGN NAG: 3 (!!), 1 (!), 6 (?!), 2 (?), 4 (??). */
    public static int toNag(Quality q) {
        switch (q) {
            case BRILLIANT:  return 3;
            case BEST:       return 1;
            case INACCURACY: return 6;
            case MISTAKE:    return 2;
            case BLUNDER:    return 4;
            default:         return 0;
        }
    }
}

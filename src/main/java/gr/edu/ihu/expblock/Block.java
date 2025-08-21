// Block.java
package gr.edu.ihu.expblock;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import gr.edu.ihu.expblock.ExpBlock.ExperimentConfig;

public class Block {
    String key;
    ArrayList<Record> recordsA = new ArrayList<>();
    ArrayList<Record> recordsB = new ArrayList<>();
    int falsePositives = 0;
    int recNo = 0;
    int lastRoundUsed = 0;
    public int degree = 0;
    double q = 0.0;

    private final ExperimentConfig currentConfig; 

    private final double LEVENSHTEIN_WEIGHT;
    private final double CHAR_EMBEDDING_WEIGHT;
    private final double SOUNDEX_WEIGHT;

    private final double SURNAME_WEIGHT;
    private final double NAME_WEIGHT;

    private final double SEMANTIC_SIMILARITY_WEIGHT;
    private final double SYNTACTIC_SIMILARITY_WEIGHT;

    private final double SIMILARITY_THRESHOLD;
    private final double NAME_SIMILARITY_THRESHOLD;
    private final double SURNAME_SIMILARITY_THRESHOLD;
    
    public class PutResult {
        public int truePositives;
        public int falsePositives;
    }

    public Block(String key, double q, ExperimentConfig config) {
        this.key = key;
        this.q = q;
        this.currentConfig = config; 
        this.LEVENSHTEIN_WEIGHT = config.levenshteinWeight;
        this.CHAR_EMBEDDING_WEIGHT = config.charEmbeddingWeight;
        this.SOUNDEX_WEIGHT = config.soundexWeight;
        this.SURNAME_WEIGHT = config.surnameWeight;
        this.NAME_WEIGHT = config.nameWeight;
        this.SEMANTIC_SIMILARITY_WEIGHT = config.semanticSimilarityWeight;
        this.SYNTACTIC_SIMILARITY_WEIGHT = config.syntacticSimilarityWeight;
        this.SIMILARITY_THRESHOLD = config.similarityThreshold;
        this.NAME_SIMILARITY_THRESHOLD = config.nameSimilarityThreshold;
        this.SURNAME_SIMILARITY_THRESHOLD = config.surnameSimilarityThreshold;
    }

    private int min(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     *
     * @param str1 The first string.
     * @param str2 The second string.
     * @return The Levenshtein distance.
     */
    public int editDistance(String str1, String str2) {
        int[][] distance = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 1; j <= str2.length(); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                distance[i][j] = min(
                        distance[i - 1][j] + 1,
                        distance[i][j - 1] + 1,
                        distance[i - 1][j - 1] + ((str1.charAt(i - 1) == (str2.charAt(j - 1)) ? 0 : 1))
                );
            }
        }

        return distance[str1.length()][str2.length()];
    }


    /**
     * Adds a record to the block, compares it against records from the opposing
     * source, and identifies matches.
     * @param rec The incoming record.
     * @param w The maximum window size for the block.
     * @param round The current processing round.
     * @param writer A file writer to log matches.
     * @return The number of new matching pairs found.
     */
    public PutResult put(Record rec, int w, int round, FileWriter writer) {
        // Crie uma instância da nova classe de retorno
        PutResult result = new PutResult();
        result.truePositives = 0;
        result.falsePositives = 0;

        ArrayList<Record> comparisonList = rec.origin.equals("A") ? recordsB : recordsA;
        ArrayList<Record> destinationList = rec.origin.equals("A") ? recordsA : recordsB;

         for (Record existingRecord : comparisonList) {

            double levenshteinNameScore = 1.0 - ((double) editDistance(existingRecord.name, rec.name) / Math.max(existingRecord.name.length(), rec.name.length()));
            double levenshteinSurnameScore = 1.0 - ((double) editDistance(existingRecord.surname, rec.surname) / Math.max(existingRecord.surname.length(), rec.surname.length()));
            
            // Obtenha todos os scores para o nome e sobrenome
            SimilarityService.SimilarityScores nameScores = SimilarityService.getScores(existingRecord.name, rec.name, this.currentConfig);
            SimilarityService.SimilarityScores surnameScores = SimilarityService.getScores(existingRecord.surname, rec.surname, this.currentConfig);

            // Calcule a similaridade sintática combinada para os nomes
            double combinedSyntacticNameScore = (levenshteinNameScore  * LEVENSHTEIN_WEIGHT) +
                                                (nameScores.charEmbeddingScore * CHAR_EMBEDDING_WEIGHT) +
                                                (nameScores.soundexScore * SOUNDEX_WEIGHT);

            // Calcule a similaridade sintática combinada para os sobrenomes
            double combinedSyntacticSurnameScore = (levenshteinSurnameScore * LEVENSHTEIN_WEIGHT) +
                                                    (surnameScores.charEmbeddingScore * CHAR_EMBEDDING_WEIGHT) +
                                                    (surnameScores.soundexScore * SOUNDEX_WEIGHT);

            // Calcule a pontuação final de nome e sobrenome usando os pesos semânticos e sintáticos
            double finalNameScore = (nameScores.semanticScore * SEMANTIC_SIMILARITY_WEIGHT) +
                                    (combinedSyntacticNameScore * SYNTACTIC_SIMILARITY_WEIGHT);
            
            double finalSurnameScore = (surnameScores.semanticScore * SEMANTIC_SIMILARITY_WEIGHT) +
                                    (combinedSyntacticSurnameScore * SYNTACTIC_SIMILARITY_WEIGHT);

            // Calcule a pontuação final total
            double finalScore = (finalNameScore * NAME_WEIGHT) + (finalSurnameScore * SURNAME_WEIGHT);

            boolean originalLogicMatch = (finalNameScore >= NAME_SIMILARITY_THRESHOLD) && (finalSurnameScore >= SURNAME_SIMILARITY_THRESHOLD);
            if (originalLogicMatch || finalScore >= SIMILARITY_THRESHOLD) {
                if (rec.getIdNo().equals(existingRecord.getIdNo())) {
                    result.truePositives++; // True Positive (TP)
                    String s = String.format("MATCH (Score: %.2f): %s (%s, %s) <-> %s (%s, %s)",
                            finalScore, existingRecord.id, existingRecord.surname, existingRecord.name, rec.id, rec.surname, rec.name);
                    try {
                        writer.write(s + "\r\n");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else {
                    result.falsePositives++; // False Positive (FP)
                }
            }
        }

        if (recordsA.size() + recordsB.size() == w) {
            Random r = new Random();
            if (recordsA.size() > recordsB.size()) {
                recordsA.remove(r.nextInt(recordsA.size()));
            } else if (!recordsB.isEmpty()) { 
                recordsB.remove(r.nextInt(recordsB.size()));
            }
        }
        destinationList.add(rec);

        this.recNo++;
        this.lastRoundUsed = round;
        return result;
    }

    /**
     * Calculates the "degree" of the block, representing its size and activity.
     * @param avg The average number of records per block across the system.
     * @param currentRound The current processing round.
     */
    public void setDegree(int avg, int currentRound) {
        double activity = (currentRound > 0) ? ((double) this.lastRoundUsed / currentRound) : 0;
        this.degree = (avg > 0) ? (int) Math.floor((this.recNo * activity) / avg) : 0;
    }
}

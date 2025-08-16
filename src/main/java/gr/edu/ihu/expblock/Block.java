// Block.java
package gr.edu.ihu.expblock;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class Block {
    String key;
    ArrayList<Record> recordsA = new ArrayList<>();
    ArrayList<Record> recordsB = new ArrayList<>();
    
    int recNo = 0;
    int lastRoundUsed = 0;
    public int degree = 0;
    double q = 0.0;

    private static final double SURNAME_WEIGHT = 0.6;
    private static final double NAME_WEIGHT = 0.4;

    private static final double SEMANTIC_SIMILARITY_WEIGHT = 0.6; 
    private static final double SYNTACTIC_SIMILARITY_WEIGHT = 0.4; 

    private static final double SIMILARITY_THRESHOLD = 0.85; 

    public Block(String key, double q) {
        this.key = key;
        this.q = q;
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
    public int put(Record rec, int w, int round, FileWriter writer) {
        int matchingPairsNo = 0;

        ArrayList<Record> comparisonList = rec.origin.equals("A") ? recordsB : recordsA;
        ArrayList<Record> destinationList = rec.origin.equals("A") ? recordsA : recordsB;

        for (Record existingRecord : comparisonList) {
            
            double nameSimilarity = SimilarityService.getCombinedSimilarity(
                existingRecord.name, rec.name, SEMANTIC_SIMILARITY_WEIGHT, SYNTACTIC_SIMILARITY_WEIGHT);
            double surnameSimilarity = SimilarityService.getCombinedSimilarity(
                existingRecord.surname, rec.surname, SEMANTIC_SIMILARITY_WEIGHT, SYNTACTIC_SIMILARITY_WEIGHT);

            double finalScore = (nameSimilarity * NAME_WEIGHT) + (surnameSimilarity * SURNAME_WEIGHT);

            if (finalScore >= SIMILARITY_THRESHOLD) {
                if (rec.getIdNo().equals(existingRecord.getIdNo())) {
                    matchingPairsNo++;
                    String s = String.format("MATCH (Score: %.2f): %s (%s, %s) <-> %s (%s, %s)",
                            finalScore, existingRecord.id, existingRecord.surname, existingRecord.name, rec.id, rec.surname, rec.name);
                    try {
                        System.out.println(s); 
                        writer.write(s + "\r\n");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
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
        return matchingPairsNo;
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

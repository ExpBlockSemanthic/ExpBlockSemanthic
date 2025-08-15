// Block.java
package gr.edu.ihu.expblock;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class Block {
    String key;
    ArrayList<Record> arr = new ArrayList<>();
    int recNo = 0;
    int lastRoundUsed = 0;
    public int degree = 0;
    double q = 0.0;

    private static final double SIMILARITY_THRESHOLD = 0.85; 

    public Block(String key, double q) {
        this.key = key;
        this.q = q;
    }

    public int put(Record rec, int w, int round, FileWriter writer) {
        int matchingPairsNo = 0;

        for (Record existingRecord : arr) {
            if (!existingRecord.origin.equals(rec.origin)) {

                double nameSimilarity = SimilarityService.getCombinedSimilarity(existingRecord.name, rec.name);
                double surnameSimilarity = SimilarityService.getCombinedSimilarity(existingRecord.surname, rec.surname);

                double finalScore = (nameSimilarity * 0.4) + (surnameSimilarity * 0.6);

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
        }

        if (arr.size() == w) {
            Random r = new Random();
            arr.remove(r.nextInt(arr.size()));
        }
        arr.add(rec);

        this.recNo++;
        this.lastRoundUsed = round;
        return matchingPairsNo;
    }

    public void setDegree(int avg, int currentRound) {
        double activity = (currentRound > 0) ? ((double) this.lastRoundUsed / currentRound) : 0;
        this.degree = (avg > 0) ? (int) Math.floor((this.recNo * activity) / avg) : 0;
    }
}
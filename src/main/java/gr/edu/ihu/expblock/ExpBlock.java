// ExpBlock.java
package gr.edu.ihu.expblock;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.codec.language.Soundex;

public class ExpBlock {
    private static final Soundex soundex = new Soundex();

    public double epsilon;
    public double delta = 0.1;
    public double q;
    public int w;
    public int b;
    public int globalRecNo = 0;
    public int occupied = 0;
    public double xi = .08;
    public int currentRound = 1;
    public int matchingPairsNo = 0;
    public int falsePositivesNo = 0;
    public int trulyMatchingPairsNo = 1000000;
    public MinHash minHash = new MinHash();
    public static FileWriter writer;
    public Block[] arr;
    IntStream rS;
    int[] r;
    int noRandoms = 5000;
    private final ExperimentConfig currentConfig;

    public ExpBlock(double epsilon, double q, int b, ExperimentConfig config) {
        this.currentConfig = config;
        try {
            this.epsilon = epsilon;
            this.b = b;
            this.q = q;
            this.arr = new Block[this.b];
             // Salva a configuração

            for (int i = 0; i < 10; i++) {
                IntStream rS = new SplittableRandom().ints(this.noRandoms / 10, 0, this.b);
                int[] r = rS.toArray();
                Arrays.sort(r);
                this.r = ArrayUtils.addAll(this.r, r);
            }
            this.w = (int) Math.ceil(3 * Math.log(2 / this.delta) / (this.q * (this.epsilon * this.epsilon)));
            writer = new FileWriter("results.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void put(Record rec) {
        if (this.occupied == b) {
            int avg = this.globalRecNo / b;
            if (avg == 0) avg = 1;
    
            int v = 0;
            int j = 0;
            int i = r[j];
            while (v < (int) Math.floor((xi * b))) {
                Block block = arr[i];
                if (block == null) {
                    j++;
                    if (j == this.noRandoms) j = 0;
                    i = r[j];
                    continue;
                }
                block.setDegree(avg, currentRound);
                if (block.degree <= 0) {
                    arr[i] = null;
                    v++;
                } else {
                    block.recNo = block.recNo - avg;
                }
                j++;
                if (j == this.noRandoms) j = 0;
                i = r[j];
            }
            this.occupied = this.occupied - ((int) Math.floor((xi * b)));
            currentRound++;
        }
    
        this.globalRecNo++;
        String key = rec.getBlockingKey(minHash);
    
        boolean blockExists = false;
        int emptyPos = -1;
    
        for (int i = 0; i < arr.length; i++) {
            Block block = arr[i];
            if (block != null) {
                double exactMatchScore = block.key.equals(key) ? 1.0 : 0.0;
                double finalScore = exactMatchScore;
            
    
                if (finalScore >= currentConfig.similarityThreshold) {
                    Block.PutResult result = block.put(rec, w, currentRound, writer);
                    this.matchingPairsNo += result.truePositives;
                    this.falsePositivesNo += result.falsePositives;
                    blockExists = true;
                    break;
                }
            } else {
                emptyPos = i;
            }
        }
    
        if (!blockExists) {
            Block newBlock = new Block(key, this.q, this.currentConfig);
            this.occupied++;
            Block.PutResult result = newBlock.put(rec, w, currentRound, writer);
            if (emptyPos != -1) {
                arr[emptyPos] = newBlock;
            } else {
                for (int i = 0; i < this.b; i++) {
                    if (arr[i] == null) {
                        arr[i] = newBlock;
                        break;
                    }
                }
            }
            this.matchingPairsNo += result.truePositives;
            this.falsePositivesNo += result.falsePositives;
        }
    }    

    public static Record prepare(String[] lineInArray) {
        String surname = lineInArray[1];
        String name = lineInArray[2];
        String address = lineInArray[3];
        String town = lineInArray[4];
        String poBox = lineInArray[5];
        String id = lineInArray[0];
        Record rec = new Record();
        rec.id = id;
        rec.name = name;
        rec.surname = surname;
        rec.town = town;
        rec.poBox = poBox;
        rec.origin = id.startsWith("a") ? "A" : "B";
        return rec;
    }
    public static class ExperimentConfig {
        double levenshteinWeight;
        double charEmbeddingWeight;
        double soundexWeight;
        double surnameWeight;
        double nameWeight;
        double semanticSimilarityWeight;
        double syntacticSimilarityWeight;
        double similarityThreshold;
        double nameSimilarityThreshold;
        double surnameSimilarityThreshold;

        public ExperimentConfig(double lw, double cew, double sw, double suw, double nw, double ssw, double syw, double st, double nst, double sust) {
            this.levenshteinWeight = lw;
            this.charEmbeddingWeight = cew;
            this.soundexWeight = sw;
            this.surnameWeight = suw;
            this.nameWeight = nw;
            this.semanticSimilarityWeight = ssw;
            this.syntacticSimilarityWeight = syw;
            this.similarityThreshold = st;
            this.nameSimilarityThreshold = nst;
            this.surnameSimilarityThreshold = sust;
        }
    }

    public static void main(String[] args) {
        // ================== CONFIGURAÇÕES DOS EXPERIMENTOS ==================
        List<ExperimentConfig> configs = new ArrayList<>();
        //configs.add(new ExperimentConfig(1.0, 0.0, 0.0, 0.5, 0.5, 0.0, 1.0, 0.4, 0.8, 0.8)); // Config 1
        
        //configs.add(new ExperimentConfig(1.0, 0.0, 0.0, 0.5, 0.5, 0.0, 1.0, 0.6, 0.8, 0.8)); // Config 2
        //configs.add(new ExperimentConfig(1.0, 0.0, 0.0, 0.5, 0.5, 0.0, 1.0, 0.8, 0.8, 0.8)); // Config 3
        

        //configs.add(new ExperimentConfig(0.7, 0.3, 0.0, 0.5, 0.5, 0.0, 1.0, 0.8, 0.8, 0.8)); // Config 4
        //configs.add(new ExperimentConfig(0.5, 0.5, 0.0, 0.5, 0.5, 0.0, 1.0, 0.8, 0.8, 0.8)); // Config 5
        //configs.add(new ExperimentConfig(0.0, 1.0, 0.0, 0.5, 0.5, 0.0, 1.0, 0.8, 0.8, 0.8)); // Config 6
        //configs.add(new ExperimentConfig(0.3, 0.7, 0.0, 0.5, 0.5, 0.0, 1.0, 0.8, 0.8, 0.8)); // Config 7

        configs.add(new ExperimentConfig(0.7, 0.3, 0.0, 0.5, 0.5, 0.3, 0.7, 0.8, 0.8, 0.8)); // Config 8
        //configs.add(new ExperimentConfig(0.7, 0.3, 0.0, 0.5, 0.5, 0.5, 0.5, 0.8, 0.8, 0.8)); // Config 9
        //configs.add(new ExperimentConfig(0.7, 0.3, 0.0, 0.5, 0.5, 0.7, 0.3, 0.8, 0.8, 0.8)); // Config 10
        //configs.add(new ExperimentConfig(0.7, 0.3, 0.0, 0.5, 0.5, 1.0, 0.0, 0.8, 0.8, 0.8)); // Config 11
       
        
        String word2VecModelPath = "GoogleNews-vectors-negative300.bin";
        SimilarityService.initialize(word2VecModelPath);

        // ================== LOOP PARA EXECUTAR CADA EXPERIMENTO 10x ==================
        for (int i = 0; i < configs.size(); i++) {
            ExperimentConfig currentConfig = configs.get(i);
            System.out.println("==================== CONFIGURAÇÃO " + (i + 1) + " ====================");

            for (int j = 0; j < 10; j++) { // Loop para repetir cada experimento 10 vezes
                System.out.println("\n--- EXPERIMENTO " + (i + 1) + ", EXECUÇÃO " + (j + 1) + " ---");

                // Crie uma nova instância de ExpBlock para cada experimento
                ExpBlock e = new ExpBlock(0.1, 2.0 / 3, 1000, currentConfig);

                int recNoA = 0;
                int recNoB = 0;
                long startTime = System.currentTimeMillis();

                String fileA = "C:\\Users\\Pichau\\Desktop\\PASTAS\\faculdade\\BCC\\11_periodo_2025_1\\ProjetoBD\\ExpBlockSemanthic\\target\\test_voters_A.txt";
                String fileB = "C:\\Users\\Pichau\\Desktop\\PASTAS\\faculdade\\BCC\\11_periodo_2025_1\\ProjetoBD\\ExpBlockSemanthic\\target\\test_voters_B.txt";

                try (CSVReader readerA = new CSVReader(new FileReader(fileA));
                    CSVReader readerB = new CSVReader(new FileReader(fileB))) {

                    String[] lineInArray1, lineInArray2;
                    boolean fileAHasNext = true;
                    boolean fileBHasNext = true;

                    while (fileAHasNext || fileBHasNext) {
                        if (fileAHasNext) {
                            lineInArray1 = readerA.readNext();
                            if (lineInArray1 != null) {
                                if (lineInArray1.length >= 6) {
                                    recNoA++;
                                    Record rec1 = prepare(lineInArray1);
                                    e.put(rec1);
                                }
                            } else {
                                fileAHasNext = false;
                            }
                        }
                        if (fileBHasNext) {
                            lineInArray2 = readerB.readNext();
                            if (lineInArray2 != null) {
                                if (lineInArray2.length >= 6) {
                                    recNoB++;
                                    Record rec2 = prepare(lineInArray2);
                                    e.put(rec2);
                                }
                            } else {
                                fileBHasNext = false;
                            }
                        }
                        if ((recNoA + recNoB) % 10000 == 0 && (recNoA + recNoB) > 0) {
                            System.out.println("====== Processed " + (recNoA + recNoB) + " records. Identified " + e.matchingPairsNo + " matching pairs.");
                        }
                    }
                } catch (IOException | CsvValidationException ex) {
                    ex.printStackTrace();
                }

                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;

                // Crie um nome de arquivo único para este experimento e execução.
                String outputFileName = "results_config_" + (i + 1) + "_exec_" + (j + 1) + ".txt";

                try (FileWriter outputWriter = new FileWriter(outputFileName)) {
                    double precision = 0.0;
                    if ((e.matchingPairsNo + e.falsePositivesNo) > 0) {
                        precision = (double) e.matchingPairsNo / (e.matchingPairsNo + e.falsePositivesNo);
                    }
                    double recall = (double) e.matchingPairsNo / e.trulyMatchingPairsNo;
                
                    String json = "{\n" +
                            "  \"configIndex\": " + (i + 1) + ",\n" +
                            "  \"executionIndex\": " + (j + 1) + ",\n" +
                            "  \"configurations\": {\n" +
                            "    \"levenshteinWeight\": " + currentConfig.levenshteinWeight + ",\n" +
                            "    \"charEmbeddingWeight\": " + currentConfig.charEmbeddingWeight + ",\n" +
                            "    \"soundexWeight\": " + currentConfig.soundexWeight + ",\n" +
                            "    \"surnameWeight\": " + currentConfig.surnameWeight + ",\n" +
                            "    \"nameWeight\": " + currentConfig.nameWeight + ",\n" +
                            "    \"semanticSimilarityWeight\": " + currentConfig.semanticSimilarityWeight + ",\n" +
                            "    \"syntacticSimilarityWeight\": " + currentConfig.syntacticSimilarityWeight + ",\n" +
                            "    \"similarityThreshold\": " + currentConfig.similarityThreshold + ",\n" +
                            "    \"nameSimilarityThreshold\": " + currentConfig.nameSimilarityThreshold + ",\n" +
                            "    \"surnameSimilarityThreshold\": " + currentConfig.surnameSimilarityThreshold + "\n" +
                            "  },\n" +
                            "  \"results\": {\n" +
                            "    \"elapsedTimeSeconds\": " + (elapsedTime / 1000.0) + ",\n" +
                            "    \"totalRecordsProcessed\": " + (recNoA + recNoB) + ",\n" +
                            "    \"matchingPairsNo\": " + e.matchingPairsNo + ",\n" +
                            "    \"falsePositivesNo\": " + e.falsePositivesNo + ",\n" +
                            "    \"totalPairsIdentified\": " + (e.matchingPairsNo + e.falsePositivesNo) + ",\n" +
                            "    \"precision\": " + precision + ",\n" +
                            "    \"recall\": " + recall + "\n" +
                            "  }\n" +
                            "}";
                
                    outputWriter.write(json);
                
                    System.out.println("Resultados da Configuração " + (i + 1) + ", Execução " + (j + 1) + " salvos em " + outputFileName);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
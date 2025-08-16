// Record.java
package gr.edu.ihu.expblock;

/**
 * Represents a single record from a data source, containing personal information
 * used for entity resolution.
 *
 * @author Administrator
 */
public class Record {

    public String id;
    public String name;
    public String surname;
    public String town;
    public String poBox;
    public String origin;
    public int evictions = 0;
    public int survivals = 0;
    public String bKey = "";

    /**
     * Default constructor.
     */
    public Record() {
    }

    /**
     * Generates a blocking key for this record. The key is used to group this
     * record with other potentially similar records in the same block.
     * @param minHash The MinHash instance to use for hashing the surname.
     * @return A string representing the blocking key.
     */
    public String getBlockingKey(MinHash minHash) {
        // The blocking key combines a hash of the surname with the postal code.
        // This helps group records that are geographically close and have similar surnames.
        this.bKey = minHash.hash(surname) + "_" + poBox;
        return this.bKey;
    }

    /**
     * Extracts the numeric part of the ID, which is used for ground-truth matching.
     * It assumes an ID format like 'a123_1' or 'b456', returning '123' or '456'.
     * @return The core numeric identifier of the record.
     */
    public String getIdNo() {
        if (this.id.indexOf("_") > 0) {
            return id.substring(1, this.id.indexOf("_"));
        }
        return id.substring(1);
    }
    

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSurname() {
        return surname;
    }

    public String getTown() {
        return town;
    }

    public String getPoBox() {
        return poBox;
    }

    public String getOrigin() {
        return origin;
    }

    /**
     * Provides a human-readable string representation of the Record object.
     * @return A formatted string with the record's key attributes.
     */
    @Override
    public String toString() {
        return String.format("Record{id='%s', name='%s', surname='%s', origin='%s'}",
                id, name, surname, origin);
    }
}

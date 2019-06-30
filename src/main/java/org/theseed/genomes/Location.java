/**
 *
 */
package org.theseed.genomes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

/**
 * This class implements a location.  The location is bacterial, so it represents multiple places on a single
 * strand of a single contig.  It has two subclasses-- FLocation for the plus strand and BLocation for the minus
 * strand.
 *
 * @author Bruce Parrello
 *
 */
public abstract class Location implements Comparable<Location>, Cloneable {

    // FIELDS

    /** ID of the contig containing this location */
    protected String contigId;
    /** list of regions covered by the location */
    protected ArrayList<Region> regions;
    /** TRUE if this location is valid, else FALSE */
    protected boolean valid;


    /**
     * Construct a blank location on a particular contig.
     *
     * @param contigId	ID of the contig containing this location
     */
    public Location(String contigId) {
        this.contigId = contigId;
        this.regions = new ArrayList<Region>(1);
        this.valid = true;
    }

    /**
     * @return the start position (1-based)
     */
    public abstract int getBegin();

    /**
     * @return the end position (1-based)
     */
    public abstract int getEnd();

    /**
     * @return the strand (+ or -)
     */
    public abstract char getDir();

    /**
     * Add a new region to this location.
     */
    public abstract void addRegion(int begin, int length);

    /**
     * @return the leftmost position (1-based)
     */
    public int getLeft() {
        Region first = regions.get(0);
        return first.getLeft();
    }
    /**
     * @return the rightmost position (1-based)
     */
    public int getRight() {
        Region last = this.lastRegion();
        return last.getRight();
    }
    /**
     * @return the overall length
     */
    public int getLength() {
        return this.getRight() + 1 - this.getLeft();
    }

    /**
     * @return the contigId
     */
    public String getContigId() {
        return contigId;
    }

    /**
     * @param contigId the contigId to set
     */
    public void setContigId(String contigId) {
        this.contigId = contigId;
    }

    /**
     * @return TRUE if this location is multi-regional, else FALSE
     */
    public boolean isSegmented() {
        return (this.regions.size() > 1);
    }

    /**
     * Mark this location as invalid.
     */
    public void invalidate() {
        this.valid = false;
    }

    /**
     * Create a simple one-region location from this one.
     */
    public Location regionOf() {
        Location retVal = this.createEmpty();
        retVal.putRegion(this.getLeft(), this.getRight());
        return retVal;
    }

    /**
     * Compare two locations.  Locations are first sorted by contig. On the same contig, the earliest
     * location will compare first.  If two locations at the same place, the shorter one compares first.
     * If they are the same length and start at the same place, the forward strand compares before the
     * reverse strand.
     *
     *  @return a negative number if this location is to the left of arg0 or at the same location and
     *  		longer
     */
    @Override
    public int compareTo(Location arg0) {
        int retVal;
        // Start by comparing contig IDs.
        retVal = this.getContigId().compareTo(arg0.getContigId());
        if (retVal == 0) {
            // Contigs the same, compare start positions.
            retVal = this.getLeft() - arg0.getLeft();
            if (retVal == 0) {
                // Start positions the same, compare lengths.
                retVal = this.getRight() - arg0.getRight();
                if (retVal == 0) {
                    // Really desperate now, compare the directions.
                    retVal = this.getDir() - arg0.getDir();
                    if (retVal == 0) {
                        // Same direction, same length.  Favor the one with the fewest regions.
                        retVal = this.regions.size() - arg0.regions.size();
                        if (retVal == 0) {
                            // Now we compare the individual regions by begin location.  Note that
                            // even having more than one region is extremely rare in practice.
                            for (int i = 0; retVal == 0 && i < this.regions.size(); i++) {
                                retVal = this.regions.get(i).compareTo(arg0.regions.get(i));
                            }
                        }
                    }
                }
            }
        }
        return retVal;
    }

    /**
     * Insert a new region into this location.  The region is placed in the proper position in the
     * region list.  Unlike addRegion, this takes as input a left and right position.
     *
     * @param left		left position
     * @param right		right position
     */
    public void putRegion(int left, int right) {
        // Create a region from the incoming points.
        Region newRegion = new Region(left, right);
        int n = this.regions.size();
        int i = 0;
        while (i < n && this.regions.get(i).getLeft() < left) {
            i++;
        }
        this.regions.add(i, newRegion);
    }

    /**
     * Create an location object for a strand on a contig.
     *
     * @param contigId	ID of the contig containing the location
     * @param strand	strand ("+" or "-") containing the location
     * @param segments	list of segments to put in the location, alternating
     * 					in the form left, right, left, right ...
     *
     * @return			returns an empty FLocation or BLocation for the contig
     */
    public static Location create(String contigId, String strand, int... segments) {
        Location retVal;
        if (strand.contentEquals("+")) {
            retVal = new FLocation(contigId);
        } else {
            retVal = new BLocation(contigId);
        }
        // If we have segments from the user, we need to do some fancy footwork,
        // since they come in [left, right] pairs.
        if ((segments.length & 1) == 1) {
            throw new IllegalArgumentException("Odd number of segment specifiers in location construction.");
        } else {
            for (int i = 0; i < segments.length; i += 2) {
                retVal.putRegion(segments[i], segments[i+1]);
            }
        }
        return retVal;
    }

    /**
     * @return an empty location object on the same strand and contig as this one.
     */
    protected abstract Location createEmpty();

    /**
     * @return the collection of regions in this location
     */
    public Collection<Region> getRegions() {
        return this.regions;
    }

    /**
     * Compute the frame of a kmer relative to this location.  The kmer is assumed to b on the
     * forward strand.
     *
     * @param pos	position (1-based) on the contig of the start of the kmer
     * @param kSize	length of the kmer
     * @return	the relevant frame position, or INVALID if the location is invalid or the kmer is not
     * 			wholly inside a region
     */
    public Frame kmerFrame(int pos, int kSize) {
        Frame retVal = Frame.XX;
        int end = pos + kSize - 1;
        if (end < this.getLeft() || pos > this.getRight()) {
            // Here we are outside the location entirely.
            retVal = Frame.F0;
        } else if (this.isValid()) {
            // Find the region containing the kmer.
            Region foundRegion = null;
            for (Region region : this.regions) {
                if (pos >= region.getLeft() && end <= region.getRight()) {
                    foundRegion = region;
                }
            }
            if (foundRegion == null) {
                // Here we are overlapping the region.
                retVal = Frame.XX;
            } else {
                // Inside this location, so compute the frame.
                retVal = this.calcFrame(pos,  end, foundRegion);
            }
        }
        return retVal;
    }

    /**
     * @return the frame for a kmer inside a region of this location
     *
     * @param pos		start position of the kmer
     * @param end		end position of the kmer
     * @param region	region containing the kmer
     */
    protected abstract Frame calcFrame(int pos, int end, Region region);

    @Override
    public Object clone() {
        // Create a new copy of the location on the same contig strand.
        Location retVal = this.createEmpty();
        // Copy the other fields.
        retVal.valid = this.valid;
        // Copy the regions.
        for (Region region : regions) {
            retVal.putRegion(region.getLeft(), region.getRight());
        }
        return retVal;
    }

    @Override
    public String toString() {
        String retVal = this.contigId + String.valueOf(this.getDir());
        for (Region region : this.regions) {
            retVal += region;
        }
        return retVal;
    }

    /**
     * @return TRUE if this location is valid
     */
    public boolean isValid() {
        return this.valid;
    }

    /**
     * @return TRUE if this location wholly contains another location.
     *
     * @param other	other location to check
     */
    public boolean contains(Location other) {
        return (this.getContigId() == other.getContigId() &&
                this.getLeft() <= other.getLeft() && this.getRight() >= other.getRight());
    }

    /**
     * Change this location by moving the left position.
     *
     * @param newLeft	new left position for this location
     */
    public void setLeft(int newLeft) {
        // Insure the new left coordinate makes sense for us.
        if (newLeft > this.getRight()) {
            throw new IllegalArgumentException("New location left of " + newLeft + " is greater than right position.");
        } else {
            // Remove regions to the left of the new start.
            while (regions.get(0).getRight() < newLeft) regions.remove(0);
            // Set the new start.
            regions.get(0).setLeft(newLeft);
        }
    }

    /**
     * Change this location by moving the right position.
     *
     * @param newRight	new right position for this location
     */
    public void setRight(int newRight) {
        // Insure the new right coordinate makes sense for us.
        if (newRight < this.getLeft()) {
            throw new IllegalArgumentException("New location right of " + newRight + " is less than left position.");
        } else {
            // Remove regions to the right of the new end.
            while (this.lastRegion().getLeft() > newRight) {
                int i = this.regions.size() - 1;
                this.regions.remove(i);
            }
            // Update the right position.
            this.lastRegion().setRight(newRight);
        }
    }

    /**
     * @return the last region in this location
     */
    private Region lastRegion() {
        int i = this.regions.size() - 1;
        return this.regions.get(i);
    }

    /**
     * Modify this location to have a single region with the specified limits.
     *
     * @param left	new left position of this location
     * @param right	new right position of this location
     */
    public void setRegion(int left, int right) {
        while (this.regions.size() > 1) this.regions.remove(1);
        Region first = this.regions.get(0);
        first.setLeft(left);
        first.setRight(right);
    }

    /**
     * This nested class provides a comparator that is based solely on left position.
     */
    public static class Sorter implements Comparator<Location> {

        public Sorter() { }

        @Override
        public int compare(Location o1, Location o2) {
            return o1.getLeft() - o2.getLeft();
        }

    }

}

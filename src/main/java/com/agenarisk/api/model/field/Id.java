package com.agenarisk.api.model.field;

import java.util.Objects;

/**
 * This class represents a unique String ID
 * 
 * @author Eugene Dementiev
 */
public class Id implements Comparable<Id> {
	
	private final String value;
	private final String valueLower;

	/**
	 * Constructor of this ID.
	 * 
	 * @param value the String value of this ID
	 */
	public Id(String value) {
		this.value = value;
		this.valueLower = value.toLowerCase();
	}

	/**
	 * Returns this ID's value.
	 * 
	 * @return value of this ID
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Returns a hash code of this ID based on its value.
	 * 
	 * @return hash code of this ID
	 */
	@Override
	public int hashCode() {
		int hash = 3;
		hash = 67 * hash + Objects.hashCode(this.valueLower);
		return hash;
	}

	/**
	 * Compares this and specified objects. If the specified object is also an ID, then equality is established by case-insensitive String equality.
	 * 
	 * @param obj object to check equality against
	 * 
	 * @return true if the specified object is the same as this one, or if the specified ID's value is the same as this ones, ignoring case considerations
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		
		if (obj == null) {
			return false;
		}
		
		if (getClass() != obj.getClass()) {
			return false;
		}
		
		final Id other = (Id) obj;
		
		return Objects.equals(this.valueLower, other.valueLower);
	}

	/**
	 * Compares two ID objects by first using case-insensitive String comparison of their respective values.
	 * <br>
	 * Then, if IDs compare as same, then they are also compared taking the case into consideration.
	 * <br>
	 * Upper-case characters will precede lower-case characters.
	 * 
	 * @param o the ID to compare to
	 * 
	 * @return a negative integer, zero, or a positive integer if the value of this ID precedes the one of the specified ID
	 */
	@Override
	public int compareTo(Id o) {
		int res = this.getValue().compareToIgnoreCase(o.getValue());
		
		if (res == 0){
			return this.getValue().compareTo(o.getValue());
		}
		
		return res;
	}

	/**
	 * Returns the string representation of this ID which is its value.
	 * 
	 * @return string representation of this ID
	 */
	@Override
	public String toString() {
		return value;
	}
	
	
	
}

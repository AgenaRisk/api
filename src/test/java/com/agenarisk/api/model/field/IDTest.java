package com.agenarisk.api.model.field;

import java.util.HashSet;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;

public class IDTest {
	
	Id instance_foo;
	Id instance_foo2;
	Id instance_bar;
	Id instance_FooBar;
	Id instance_Foo;
	Id instance_BAR;
	
	@BeforeAll
	protected void setUp() throws Exception {
		instance_foo = new Id("foo");
		instance_foo2 = new Id("foo");
		instance_bar = new Id("bar");
		instance_FooBar = new Id("FooBar");
		instance_Foo = new Id("Foo");
		instance_BAR = new Id("BAR");
	}

	public void testGetValue() {
		System.out.println("getValue");
		assertEquals(instance_foo.getValue(), "foo");
		assertEquals(instance_BAR.getValue(), "BAR");
		assertFalse(instance_foo.getValue().equals("bar"));
	}

	public void testHashCode() {
		System.out.println("hashCode");
		assertEquals(instance_foo.hashCode(), instance_foo2.hashCode());
		assertFalse(instance_foo.hashCode() == instance_bar.hashCode());
	}

	public void testEquals() {
		System.out.println("equals");
		assertEquals(instance_foo, instance_foo);
		assertEquals(instance_foo, instance_foo2);
		assertEquals(instance_foo, instance_Foo);
		assertEquals(instance_bar, instance_BAR);
		
		Assertions.assertNotEquals(null, instance_foo);
		Assertions.assertNotEquals(instance_bar, instance_foo);
	}

	public void testCompareTo() {
		System.out.println("compareTo");
		assertTrue(instance_foo.compareTo(instance_foo2) == 0);
		assertTrue(instance_foo.compareTo(instance_bar) > 0);
		assertTrue(instance_bar.compareTo(instance_foo) < 0);
		assertTrue(instance_bar.compareTo(instance_Foo) < 0);
		assertTrue(instance_BAR.compareTo(instance_Foo) < 0);
		assertTrue(instance_Foo.compareTo(instance_bar) > 0);
	}

	public void testToString() {
		System.out.println("toString");
		assertEquals(instance_foo.toString(), "foo");
		assertEquals(instance_bar.toString(), "bar");
		assertEquals(instance_BAR.toString(), "BAR");
	}
	
	public void testSetAndContains() {
		System.out.println("testSetAndContains");
		HashSet<Id> set = new HashSet<>();
		set.add(instance_foo);
		set.add(instance_bar);
		assertTrue(set.contains(instance_foo2));
		assertTrue(set.contains(instance_Foo));
		assertTrue(set.contains(instance_BAR));
		assertFalse(set.contains(instance_FooBar));
	}
}

package com.agenarisk.api.model.field;

import junit.framework.TestCase;

public class IDTest extends TestCase {
	
	Id instance_foo;
	Id instance_foo2;
	Id instance_bar;
	Id instance_FooBar;
	Id instance_Foo;
	Id instance_BAR;
	
	public IDTest(String testName) {
		super(testName);
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
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
		
		assertFalse(instance_foo.equals(null));
		assertFalse(instance_foo.equals(instance_bar));
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
	
}

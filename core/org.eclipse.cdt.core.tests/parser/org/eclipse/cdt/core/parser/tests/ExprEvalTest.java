package org.eclipse.cdt.core.parser.tests;

import java.io.StringReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.cdt.core.parser.IParser;
import org.eclipse.cdt.core.parser.ParserFactory;
import org.eclipse.cdt.core.parser.ParserMode;
import org.eclipse.cdt.core.parser.ast.IASTExpression;
import org.eclipse.cdt.internal.core.parser.NullSourceElementRequestor;
import org.eclipse.cdt.internal.core.parser.ScannerInfo;

public class ExprEvalTest extends TestCase {

	public static Test suite() {
		return new TestSuite(ExprEvalTest.class);
	}
	
	public ExprEvalTest(String name) {
		super(name);
	}
	
	public void runTest(String code, int expectedValue) throws Exception {
		
		final NullSourceElementRequestor nullCallback = new NullSourceElementRequestor();
        IParser parser = ParserFactory.createParser(ParserFactory.createScanner( new StringReader( code ), null, new ScannerInfo(), null, nullCallback ), nullCallback, ParserMode.QUICK_PARSE);
		IASTExpression expression = parser.expression();
		assertEquals(expectedValue, expression.evaluateExpression());
	}
	
	public void testInteger() throws Exception {
		runTest("5;", 5);
		runTest( "33;", 33 );
	}
	
	public void testNot() throws Exception
	{
		runTest( "!1;", 0 );
		runTest( "!0;", 1 );
		runTest( "!4;", 0 ); 
		runTest( "!!4;", 1 );
	}
	
	public void testMultiplicational() throws Exception
	{
		runTest( "3 * 4;", 12 ); 
		runTest( "55 * 2;", 110 );
		runTest( "4 / 3;", 1 );
		runTest( "100/4;", 25 );
		runTest( "8 % 2;", 0 );
		runTest( "8 % 3;", 2 );
	}
	
	public void testAdditive() throws Exception
	{
		runTest( "4 + 4;", 8 );
		runTest( "4 - 4;", 0 );
	}
	
	public void testLogicalAnd() throws Exception
	{
		runTest( "4 && 5;", 1 );
		runTest( "0 && 5;", 0 );
		runTest( "5 && 0;", 0 );
		runTest( "0 && 0;", 0 );
	}
	
	public void testLogicalOr() throws Exception
	{
		runTest( "4 || 5;", 1 );
		runTest( "0 || 5;", 1 );
		runTest( "5 || 0;", 1 );
		runTest( "0 || 0;", 0 );
	}
	
	public void testRelational() throws Exception {
		runTest("1 < 2;", 1);
		runTest("2 < 1;", 0);
		runTest("2 == 1 + 1;", 1);
		runTest("2 != 1 + 1;", 0);
	}
	
	public void testBracketed() throws Exception {
		runTest("2 * (3 + 4);", 14);
	}
}

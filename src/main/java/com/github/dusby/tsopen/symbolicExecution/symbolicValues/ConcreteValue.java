package com.github.dusby.tsopen.symbolicExecution.symbolicValues;

import soot.jimple.Constant;

public class ConcreteValue implements SymbolicValueProvider {

	private Constant constant;

	public ConcreteValue(Constant c) {
		this.constant = c;
	}

	//FIXME find better solution
	@Override
	public String getContextValue() {
		return this.constant.toString().replace("\"", "").replace("\\", "");
	}
}

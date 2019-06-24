package com.github.dusby.tsopen.symbolicExecution.methodRecognizers.sms;

import java.util.List;

import com.github.dusby.tsopen.symbolicExecution.SymbolicExecution;
import com.github.dusby.tsopen.symbolicExecution.symbolicValues.SymbolicValue;
import com.github.dusby.tsopen.utils.Constants;

import soot.SootClass;
import soot.SootMethod;
import soot.Value;
import soot.tagkit.StringConstantValueTag;

public class CreateFromPduRecognition extends SmsMethodsRecognitionHandler {

	public CreateFromPduRecognition(SmsMethodsRecognitionHandler next, SymbolicExecution se) {
		super(next, se);
	}

	@Override
	public boolean processSmsMethod(SootMethod method, List<Value> args, SymbolicValue sv) {
		SootClass declaringClass = method.getDeclaringClass();
		String methodName = method.getName();
		if(methodName.equals(Constants.CREATE_FROM_PDU) && declaringClass.getName().equals(Constants.ANDROID_TELEPHONY_SMSMESSAGE)) {
			sv.addTag(new StringConstantValueTag(Constants.SMS_TAG));
			return true;
		}
		return false;
	}

}

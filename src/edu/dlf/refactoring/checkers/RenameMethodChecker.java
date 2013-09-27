package edu.dlf.refactoring.checkers;

import edu.dlf.refactoring.design.IDetectedRefactoring;
import edu.dlf.refactoring.design.IRefactoringChecker;

public class RenameMethodChecker implements IRefactoringChecker{

	@Override
	public ICheckingResult checkRefactoring(IDetectedRefactoring refactoring) {
		return new DefaultCheckingResult(true, refactoring);
	}

}

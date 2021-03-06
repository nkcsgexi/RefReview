package edu.dlf.refactoring.checkers;
import org.apache.log4j.Logger;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;

import com.google.inject.Inject;

import edu.dlf.refactoring.change.ChangeComponentInjector.BlockAnnotation;
import edu.dlf.refactoring.change.ChangeComponentInjector.MethodDeclarationAnnotation;
import edu.dlf.refactoring.change.IASTNodeChangeCalculator;
import edu.dlf.refactoring.design.ASTNodePair;
import edu.dlf.refactoring.design.IDetectedRefactoring;
import edu.dlf.refactoring.design.IImplementedRefactoring;
import edu.dlf.refactoring.design.IRefactoringChecker;
import edu.dlf.refactoring.design.ISourceChange;
import edu.dlf.refactoring.design.ISourceChange.SourceChangeType;
import edu.dlf.refactoring.detectors.ChangeCriteriaBuilder;
import edu.dlf.refactoring.detectors.SourceChangeSearcher.IChangeSearchCriteria;
import edu.dlf.refactoring.detectors.SourceChangeSearcher.IChangeSearchResult;
import edu.dlf.refactoring.refactorings.DetectedExtractMethodRefactoring;
import fj.F;
import fj.F2;
import fj.P;
import fj.P2;
import fj.data.List;
import fj.data.List.Buffer;
import fj.data.Option;

public class ExtractMethodChecker implements IRefactoringChecker{

	private final Logger logger;
	private final String methodLevel;
	private final IASTNodeChangeCalculator blockCalculator;
	
	@Inject
	public ExtractMethodChecker(
			Logger logger,
			@MethodDeclarationAnnotation String methodLevel,
			@BlockAnnotation IASTNodeChangeCalculator blockCalculator)
	{
		this.logger = logger;
		this.methodLevel = methodLevel;
		this.blockCalculator = blockCalculator;
	}
	
	
	@Override
	public ICheckingResult checkRefactoring(IDetectedRefactoring 
			detected, IImplementedRefactoring implemented) {
		if(isRefactoringSame(implemented, detected))
		{
			return new DefaultCheckingResult(true, detected);
		}
		return new DefaultCheckingResult(true, detected);
	}

	private boolean isRefactoringSame(IImplementedRefactoring implemented,
			IDetectedRefactoring detectedRefactoring) {
		Buffer<P2<Boolean,String>> results = Buffer.empty();
		results.append(checkDeclaredMethods(implemented, detectedRefactoring));
		return results.toList().isEmpty();
	}

	private List<P2<Boolean,String>> checkDeclaredMethods(IImplementedRefactoring 
			implemented, IDetectedRefactoring detectedRefactoring) {
		Option<ASTNode> op = getAddedMethod(implemented);
		if(op.isSome())
		{
			ASTNode implementedMethod = op.some();
			ASTNode detectedMethod = detectedRefactoring.getEffectedNode(
					DetectedExtractMethodRefactoring.DeclaredMethod);
			List<F2<ASTNode, ASTNode, P2<Boolean, String>>> checkers = 
				getCheckersForDeclaredMethods();
			return RefactoringCheckerUtils.performChecking(checkers, implementedMethod, 
				detectedMethod);
		}
		return List.nil();
	}

	private Option<ASTNode> getAddedMethod(IImplementedRefactoring implemented) {
		List<ISourceChange> changes = implemented.getSourceChanges();
		ChangeCriteriaBuilder builder = new ChangeCriteriaBuilder();
		final IChangeSearchCriteria criteria = builder.addSingleChangeCriteria
			(methodLevel, SourceChangeType.ADD).getSearchCriteria();
		List<ISourceChange> addedMethods = changes.bind(new F<ISourceChange, 
			List<ISourceChange>>() {
			@Override
			public List<ISourceChange> f(ISourceChange change) {
				return criteria.search(change).bind(new 
					F<IChangeSearchResult, List<ISourceChange>>() {
						@Override
						public List<ISourceChange> f(IChangeSearchResult 
								change) {
							return change.getSourceChanges().filter(new 
									F<ISourceChange, Boolean>() {		
								@Override
								public Boolean f(ISourceChange change) {
									return change.getSourceChangeLevel().
										equals(methodLevel);
								}});}});}});
		if(addedMethods.isNotEmpty())
			return Option.some(addedMethods.head().getNodeAfter());
		else
			return Option.none();
	}
	
	
	private List<F2<ASTNode, ASTNode, P2<Boolean, String>>> getCheckersForDeclaredMethods()
	{
		Buffer<F2<ASTNode, ASTNode, P2<Boolean, String>>> buffer = Buffer.empty();
		buffer.snoc(getReturnValueChecker());
		buffer.snoc(getStatementsChecker());
		return buffer.toList();
	}
	
	
	private F2<ASTNode, ASTNode, P2<Boolean, String>> getReturnValueChecker()
	{
		return new F2<ASTNode, ASTNode, P2<Boolean, String>>(){
			@Override
			public P2<Boolean, String> f(ASTNode m1, ASTNode m2) {
				Type t1 = (Type) m1.getStructuralProperty(MethodDeclaration.
					RETURN_TYPE2_PROPERTY);
				Type t2 = (Type) m2.getStructuralProperty(MethodDeclaration.
					RETURN_TYPE2_PROPERTY);								
				Boolean isSame = t1.toString().equals(t2.toString());
				String message = "Return type right.";
				if(!isSame) message = "Return type does not match.";
				return P.p(isSame, message);
			}};
	}
	
	private F2<ASTNode, ASTNode, P2<Boolean, String>> getStatementsChecker()
	{
		return new F2<ASTNode, ASTNode, P2<Boolean,String>>() {
			@Override
			public P2<Boolean, String> f(ASTNode method1, ASTNode method2) {
				ASTNode body1 = (ASTNode) method1.getStructuralProperty
					(MethodDeclaration.BODY_PROPERTY);
				ASTNode body2 = (ASTNode) method2.getStructuralProperty
					(MethodDeclaration.BODY_PROPERTY);
				ISourceChange bodyChange = blockCalculator.CalculateASTNodeChange
					(new ASTNodePair(body1, body2));
				if(bodyChange.getSourceChangeType() == SourceChangeType.NULL)
					return P.p(true, "Body right.");
				return P.p(false, "Body changed.");
			}
		};
	}
}

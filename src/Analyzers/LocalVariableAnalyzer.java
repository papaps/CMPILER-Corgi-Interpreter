package Analyzers;

import Commands.IConditionalCommand;
import Commands.IControlledCommand;
import Commands.MappingCommand;
import ErrorChecker.MultipleVarDecChecker;
import ErrorChecker.TypeChecker;
import Execution.ExecutionManager;
import GeneratedAntlrClasses.CorgiLexer;
import GeneratedAntlrClasses.CorgiParser;
import Representations.CorgiValue;
import Semantics.LocalScope;
import Semantics.LocalScopeCreator;
import Statements.StatementControlOverseer;
import Utlities.IdentifiedTokenHolder;
import Utlities.KeywordRecognizer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

public class LocalVariableAnalyzer implements ParseTreeListener {

    private final static String TAG = "MobiProg_LocalVariableAnalyzer";

    private final static String FINAL_TYPE_KEY = "FINAL_TYPE_KEY";
    private final static String PRIMITIVE_TYPE_KEY = "PRIMITIVE_TYPE_KEY";
    private final static String IDENTIFIER_KEY = "IDENTIFIER_KEY";
    private final static String IDENTIFIER_VALUE_KEY = "IDENTIFIER_VALUE_KEY";

    private IdentifiedTokenHolder identifiedTokenHolder;
    private boolean executeMappingImmediate = false;
    private boolean hasPassedArrayDeclaration = false;

    public LocalVariableAnalyzer() {

    }

    public void analyze(CorgiParser.LocalVariableDeclarationContext localVarDecCtx) {
        this.identifiedTokenHolder = new IdentifiedTokenHolder();

        ParseTreeWalker treeWalker = new ParseTreeWalker();
        treeWalker.walk(this, localVarDecCtx);

    }

    @Override
    public void visitTerminal(TerminalNode node) {
        // TODO Auto-generated method stub
    }

    @Override
    public void visitErrorNode(ErrorNode node) {
        // TODO Auto-generated method stub

    }

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
        this.analyzeVariables(ctx);
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
        // TODO Auto-generated method stub

    }

    private void analyzeVariables(ParserRuleContext ctx) {

        if(ctx instanceof CorgiParser.VariableModifierContext){
            CorgiParser.VariableModifierContext varModCtx = (CorgiParser.VariableModifierContext) ctx;

            if (ctx.getTokens(CorgiLexer.FINAL).size() > 0) {
                this.identifiedTokenHolder.addToken(FINAL_TYPE_KEY, varModCtx.getText());
            }

        }

        if(ctx instanceof CorgiParser.TypeTypeContext) {
            CorgiParser.TypeTypeContext typeCtx = (CorgiParser.TypeTypeContext) ctx;
            //clear tokens for reuse
            //this.identifiedTokens.clearTokens();

            if(ClassAnalyzer.isPrimitiveDeclaration(typeCtx)) {
                CorgiParser.PrimitiveTypeContext primitiveTypeCtx = typeCtx.primitiveType();
                this.identifiedTokenHolder.addToken(PRIMITIVE_TYPE_KEY, primitiveTypeCtx.getText());

            }

            //check if its array declaration
            else if(ClassAnalyzer.isPrimitiveArrayDeclaration(typeCtx)) {
                //Console.log(LogType.DEBUG, "Primitive array declaration: " +typeCtx.getText());
                ArrayAnalyzer arrayAnalyzer = new ArrayAnalyzer(this.identifiedTokenHolder, LocalScopeCreator.getInstance().getActiveLocalScope());
                arrayAnalyzer.analyze(typeCtx.getParent());
                this.hasPassedArrayDeclaration = true;
            }

            //this is for class type ctx
            else {
                //a string identified
                if(typeCtx.classOrInterfaceType().getText().contains(KeywordRecognizer.PRIMITIVE_TYPE_STRING)) {
                    CorgiParser.ClassOrInterfaceTypeContext classInterfaceCtx = typeCtx.classOrInterfaceType();
                    this.identifiedTokenHolder.addToken(PRIMITIVE_TYPE_KEY, classInterfaceCtx.getText());
                }
            }


        }

        else if(ctx instanceof CorgiParser.VariableDeclaratorContext) {

            CorgiParser.VariableDeclaratorContext varCtx = (CorgiParser.VariableDeclaratorContext) ctx;

            if(this.hasPassedArrayDeclaration) {

                return;
            }

            //check for duplicate declarations
            if(!this.executeMappingImmediate) {
                MultipleVarDecChecker multipleDeclaredChecker = new MultipleVarDecChecker(varCtx.variableDeclaratorId());
                multipleDeclaredChecker.verify();
            }

            this.identifiedTokenHolder.addToken(IDENTIFIER_KEY, varCtx.variableDeclaratorId().getText());
            this.createCorgiValue();

            if(varCtx.variableInitializer() != null) {

                //we do not evaluate strings.
                if(this.identifiedTokenHolder.containsTokens(PRIMITIVE_TYPE_KEY)) {
                    String primitiveTypeString = this.identifiedTokenHolder.getToken(PRIMITIVE_TYPE_KEY);

                    if(primitiveTypeString.contains(KeywordRecognizer.PRIMITIVE_TYPE_STRING)) {
                        this.identifiedTokenHolder.addToken(IDENTIFIER_VALUE_KEY, varCtx.variableInitializer().getText());
                    }
                }

                this.processMapping(varCtx);

                LocalScope localScope = LocalScopeCreator.getInstance().getActiveLocalScope();
                CorgiValue declaredCorgiValue = localScope.searchVariableIncludingLocal(varCtx.variableDeclaratorId().getText());

                //type check the mobivalue
                TypeChecker typeChecker = new TypeChecker(declaredCorgiValue, varCtx.variableInitializer().expression());
                typeChecker.verify();
            }

        }

    }

    /*
     * Local variable analyzer is also used for loops. Whenever there is a loop,
     * mapping command should be executed immediately to update the value in the symbol table.
     * Otherwise, it proceeds normally.
     */
    private void processMapping(CorgiParser.VariableDeclaratorContext varCtx) {
        if(this.executeMappingImmediate) {
            MappingCommand mappingCommand = new MappingCommand(varCtx.variableDeclaratorId().getText(), varCtx.variableInitializer().expression());
            mappingCommand.execute();
        }
        else {
            MappingCommand mappingCommand = new MappingCommand(varCtx.variableDeclaratorId().getText(), varCtx.variableInitializer().expression());

            StatementControlOverseer statementControl = StatementControlOverseer.getInstance();
            //add to conditional controlled command
            if(statementControl.isInConditionalCommand()) {
                IConditionalCommand conditionalCommand = (IConditionalCommand) statementControl.getActiveControlledCommand();

                if(statementControl.isInPositiveRule()) {
                    conditionalCommand.addPositiveCommand(mappingCommand);
                }
                else {
                    conditionalCommand.addNegativeCommand(mappingCommand);
                }
            }

            else if(statementControl.isInControlledCommand()) {
                IControlledCommand controlledCommand = (IControlledCommand) statementControl.getActiveControlledCommand();
                controlledCommand.addCommand(mappingCommand);
            }
//            else if (statementControl.isInAttemptCommand()) {
//                IAttemptCommand attemptCommand = (IAttemptCommand) statementControl.getActiveControlledCommand();
//
//                if(statementControl.isInTryBlock()) {
//                    attemptCommand.addTryCommand(mappingCommand);
//                } else {
//                    attemptCommand.addCatchCommand(statementControl.getCurrentCatchType(), mappingCommand);
//                }
//            }
            else {
                ExecutionManager.getInstance().addCommand(mappingCommand);
            }

        }
    }

    public void markImmediateExecution() {
        this.executeMappingImmediate = true;
    }

    /*
     * Attempts to create an intermediate representation of the variable once a sufficient amount of info has been retrieved.
     */
    private void createCorgiValue() {

        if(this.identifiedTokenHolder.containsTokens(PRIMITIVE_TYPE_KEY, IDENTIFIER_KEY)) {

            String primitiveTypeString = this.identifiedTokenHolder.getToken(PRIMITIVE_TYPE_KEY);
            String identifierString = this.identifiedTokenHolder.getToken(IDENTIFIER_KEY);
            String identifierValueString = null;

            LocalScope localScope = LocalScopeCreator.getInstance().getActiveLocalScope();

            if(this.identifiedTokenHolder.containsTokens(IDENTIFIER_VALUE_KEY)) {
                identifierValueString = this.identifiedTokenHolder.getToken(IDENTIFIER_VALUE_KEY);
                localScope.addInitializedVariable(primitiveTypeString, identifierString, identifierValueString);

                if (this.identifiedTokenHolder.containsTokens(FINAL_TYPE_KEY))
                    localScope.addFinalInitVariable(primitiveTypeString, identifierString, identifierValueString);

            }
            else {
                localScope.addEmptyVariable(primitiveTypeString, identifierString);

                if (this.identifiedTokenHolder.containsTokens(FINAL_TYPE_KEY))
                    localScope.addFinalEmptyVariable(primitiveTypeString, identifierString);
            }

            //remove the following tokens
            this.identifiedTokenHolder.removeToken(IDENTIFIER_KEY);
            this.identifiedTokenHolder.removeToken(IDENTIFIER_VALUE_KEY);

        }
    }
}
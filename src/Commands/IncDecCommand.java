package Commands;

import ErrorChecker.ConstChecker;
import ErrorChecker.UndeclaredChecker;
import GeneratedAntlrClasses.CorgiLexer;
import GeneratedAntlrClasses.CorgiParser;
import Mapping.IValueMapper;
import Mapping.IdentifierMapper;
import Representations.CorgiValue;
import Representations.PrimitiveType;

public class IncDecCommand implements ICommand {

    private CorgiParser.ExpressionContext exprCtx;
    private int tokenSign;

    public IncDecCommand(CorgiParser.ExpressionContext exprCtx, int tokenSign) {
        this.exprCtx = exprCtx;
        this.tokenSign = tokenSign;

        ConstChecker constChecker = new ConstChecker(this.exprCtx);
        constChecker.verify();

        UndeclaredChecker undeclaredChecker = new UndeclaredChecker(this.exprCtx);
        undeclaredChecker.verify();
    }

    @Override
    public void execute() {

        IValueMapper leftHandMapper = new IdentifierMapper(
                this.exprCtx.getText());
        leftHandMapper.analyze(this.exprCtx);

        CorgiValue corgiValue = leftHandMapper.getCorgiValue();

        if(!corgiValue.isFinal())
            this.performOperation(corgiValue);

    }

    /*
     * Attempts to perform an increment/decrement operation
     */
    private void performOperation(CorgiValue corgiValue) {
        if(corgiValue.getPrimitiveType() == PrimitiveType.INT) {
            int value = Integer.valueOf(corgiValue.getValue().toString());

            if(this.tokenSign == CorgiLexer.INC) {
                value++;
                corgiValue.setValue(String.valueOf(value));
            }
            else if(this.tokenSign == CorgiLexer.DEC) {
                value--;
                corgiValue.setValue(String.valueOf(value));
            }
        }
        else if(corgiValue.getPrimitiveType() == PrimitiveType.FLOAT) {
            float value = Float.valueOf(corgiValue.getValue().toString());

            if(this.tokenSign == CorgiLexer.INC) {
                value++;
                corgiValue.setValue(String.valueOf(value));
            }
            else if(this.tokenSign == CorgiLexer.DEC) {
                value--;
                corgiValue.setValue(String.valueOf(value));
            }
        }
    }

    public String getIdentifierString() {
        return exprCtx.primary().Identifier().getText();
    }
}
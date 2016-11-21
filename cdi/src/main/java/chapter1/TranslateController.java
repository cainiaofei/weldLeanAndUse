package chapter1;

import javax.inject.Inject;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;


public class TranslateController {

   @Inject TextTranslator textTranslator; 


   private String inputText;

   private String translation;

   // JSF action method, perhaps

   public void translate() {
      translation = textTranslator.translate(inputText);
   }


   public String getInputText() {
      return inputText;
   }

   public void setInputText(String text) {
      this.inputText = text;
   }

   public String getTranslation() {
      return translation;
   }

   public static void main(String[] args){
	   Weld weld = new Weld();
	   WeldContainer container = weld.initialize();
	   TranslateController tc = container.instance().select(TranslateController.class).get();
	   TextTranslator tt = container.instance().select(TextTranslator.class).get();
	   tc.translate();
	   weld.shutdown();
   }
}
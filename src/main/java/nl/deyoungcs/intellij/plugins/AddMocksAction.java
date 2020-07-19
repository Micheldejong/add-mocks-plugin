package nl.deyoungcs.intellij.plugins;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;


public class AddMocksAction extends AnAction {

    private PsiElementFactory psiElementFactory;
    private JavaCodeStyleManager javaCodeStyleManager;

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        addMocks(anActionEvent);
    }

    private void addMocks(@NotNull AnActionEvent anActionEvent) {
        Project project = anActionEvent.getData(CommonDataKeys.PROJECT);
        PsiElement element = anActionEvent.getData(CommonDataKeys.PSI_ELEMENT);

        PsiClass testClass = (PsiClass) element;
        String simpleName = testClass.getName();

        String actualClassName = simpleName.replace("Test", "");
        PsiClass[] actualClass = PsiShortNamesCache.getInstance(project).getClassesByName(actualClassName, GlobalSearchScope.allScope(project));
        if (actualClass.length == 0) {
            JBPopup message = JBPopupFactory.getInstance().createMessage(String.format("Class %s not found", actualClassName));
            message.showCenteredInCurrentWindow(project);
            return;
        }
        psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);

        WriteCommandAction.runWriteCommandAction(project, () -> {
                    addMockFieldsWithAnnotation(testClass, actualClass[0].getFields());
                    addInjectMockField(testClass, actualClass[0]);
                    javaCodeStyleManager.shortenClassReferences(testClass);
                }
        );
    }

    private void addMockFieldsWithAnnotation(PsiClass testClass, PsiField[] fields) {
        Arrays.stream(fields)
                .filter(fieldHasNoInitializer())
                .map(this::createFieldWithMockAnnotation)
                .forEach(testClass::add);
    }

    @NotNull
    private Predicate<PsiField> fieldHasNoInitializer() {
        return field -> !field.hasInitializer();
    }

    @NotNull
    private PsiField createFieldWithMockAnnotation(PsiField field) {
        PsiField newField = psiElementFactory.createField(field.getName(), field.getType());
        Objects.requireNonNull(newField.getModifierList()).addAnnotation("org.mockito.Mock");
        return newField;
    }

    private void addInjectMockField(PsiClass testClass, PsiClass actualClass) {
        PsiField newField = psiElementFactory.createField(Objects.requireNonNull(actualClass.getName()), psiElementFactory.createType(actualClass));
        Objects.requireNonNull(newField.getModifierList()).addAnnotation("org.mockito.InjectMocks");
        testClass.add(newField);
    }
}
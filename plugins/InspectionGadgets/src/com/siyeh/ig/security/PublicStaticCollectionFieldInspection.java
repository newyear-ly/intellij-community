// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.security;

import com.intellij.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Bas Leijdekkers
 */
public class PublicStaticCollectionFieldInspection extends BaseInspection {

  final MethodMatcher myMethodMatcher = new MethodMatcher()
    .add(CommonClassNames.JAVA_UTIL_COLLECTIONS, "(empty|unmodifiable).*")
    .add("java.util.List", "of")
    .add("java.util.Set", "of")
    .add("java.util.Map", "of")
    .add("com.google.common.collect.ImmutableCollection", ".*")
    .add("com.google.common.collect.ImmutableMap", ".*")
    .add("com.google.common.collect.ImmutableMultimap", ".*")
    .add("com.google.common.collect.ImmutableTable", ".*")
    .finishDefault();

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(new ListWrappingTableModel(
      Arrays.asList(myMethodMatcher.getClassNames(), myMethodMatcher.getMethodNamePatterns()),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.method.column.title")));
    final var panel = new InspectionOptionsPanel();
    panel.addGrowing(UiUtils.createAddRemoveTreeClassChooserPanel(table, JavaBundle.message("dialog.title.choose.class")));
    return panel;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("public.static.collection.field.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    myMethodMatcher.readSettings(element);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    super.writeSettings(element);
    myMethodMatcher.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicStaticCollectionFieldVisitor();
  }

  private class PublicStaticCollectionFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.PUBLIC) || !field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiType type = field.getType();
      if (!CollectionUtils.isCollectionClassOrInterface(type) || isImmutableCollection(field)) {
        return;
      }
      registerFieldError(field);
    }

    private boolean isImmutableCollection(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
      final PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(field.getInitializer());
      if (ExpressionUtils.isNullLiteral(initializer)) {
        return true;
      }
      if (!(initializer instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null || myMethodMatcher.matches(method)) {
        return true;
      }
      if (ExpressionUtils.hasExpressionCount(methodCallExpression.getArgumentList(), 0) && "asList".equals(method.getName())) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_UTIL_ARRAYS.equals(containingClass.getQualifiedName())) {
          // empty Arrays.asList() is harmless
          return true;
        }
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(methodCallExpression.getType());
      return aClass != null && JCiPUtil.isImmutable(aClass);
    }
  }
}

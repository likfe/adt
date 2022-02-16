// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.lang.aidl.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.android.tools.idea.lang.aidl.lexer.AidlTokenTypes.*;
import com.android.tools.idea.lang.aidl.psi.*;

public class AidlParcelableDeclarationImpl extends AbstractAidlDeclarationImpl implements AidlParcelableDeclaration {

  public AidlParcelableDeclarationImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AidlVisitor) ((AidlVisitor)visitor).visitParcelableDeclaration(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AidlDeclarationName getDeclarationName() {
    return findChildByClass(AidlDeclarationName.class);
  }

}

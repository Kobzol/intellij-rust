/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsUseGroup
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.deleteWithSurroundingComma
import org.rust.lang.core.psi.ext.parentUseSpeck


/**
 * Fix that removes a use speck, possibly with its parent use item.
 */
class RemoveUseSpeckFix(useSpeck: RsUseSpeck) : LocalQuickFixOnPsiElement(useSpeck) {
    override fun getText() = "Remove unused import"
    override fun getFamilyName() = text

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val useSpeck = startElement as? RsUseSpeck ?: return
        deleteUseSpeck(useSpeck)
    }
}

private fun deleteUseSpeck(useSpeck: RsUseSpeck) {
    val parent = useSpeck.parent
    if (parent is RsUseItem) {
        parent.delete()
    } else if (parent is RsUseGroup && parent.useSpeckList.size == 1) {
        deleteUseSpeck(parent.parentUseSpeck)
    } else {
        useSpeck.deleteWithSurroundingComma()
    }
}
/*
* In file: file:///projects/personal/intellij-rust/exampleProject/src/main.rs

java.lang.IllegalStateException: Element outside of module: asd
	at org.rust.lang.core.psi.ext.RsStubbedElementImpl.getContainingMod(RsElement.kt:114)
	at org.rust.lang.core.psi.ext.RsPathImplMixin.getContainingMod(RsPath.kt:164)
	at org.rust.lang.core.resolve.NameResolutionKt.processUnqualifiedPathResolveVariants(NameResolution.kt:512)
	at org.rust.lang.core.resolve.NameResolutionKt.processPathResolveVariants(NameResolution.kt:356)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImplKt$resolvePath$result$1.invoke(RsPathReferenceImpl.kt:144)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImplKt$resolvePath$result$1.invoke(RsPathReferenceImpl.kt)
	at org.rust.lang.core.resolve.ProcessorsKt.collectPathResolveVariants(Processors.kt:101)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImplKt.resolvePath(RsPathReferenceImpl.kt:143)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImplKt.resolvePath$default(RsPathReferenceImpl.kt:142)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImpl$Resolver.invoke(RsPathReferenceImpl.kt:131)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImpl$Resolver.invoke(RsPathReferenceImpl.kt:129)
	at org.rust.lang.core.resolve.ref.RsResolveCache$resolveWithCaching$$inlined$run$lambda$1.compute(RsResolveCache.kt:99)
	at com.intellij.openapi.util.RecursionManager$1.computePreventingRecursion(RecursionManager.java:111)
	at com.intellij.openapi.util.RecursionGuard.doPreventingRecursion(RecursionGuard.java:42)
	at org.rust.lang.core.resolve.ref.RsResolveCache.resolveWithCaching(RsResolveCache.kt:99)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImpl.advancedCachedMultiResolve(RsPathReferenceImpl.kt:89)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImpl.advancedMultiResolve(RsPathReferenceImpl.kt:85)
	at org.rust.lang.core.resolve.ref.RsPathReferenceImpl.multiResolve(RsPathReferenceImpl.kt:81)
	at org.rust.lang.core.resolve.NamespaceKt.getNamespaces(Namespace.kt:81)
	at org.rust.ide.annotator.RsErrorAnnotatorKt$duplicatesByNamespace$1.invoke(RsErrorAnnotator.kt:1190)
	at org.rust.ide.annotator.RsErrorAnnotatorKt$duplicatesByNamespace$duplicates$4.invoke(RsErrorAnnotator.kt:1209)
	at org.rust.ide.annotator.RsErrorAnnotatorKt$duplicatesByNamespace$duplicates$4.invoke(RsErrorAnnotator.kt)
	at kotlin.sequences.FlatteningSequence$iterator$1.ensureItemIterator(Sequences.kt:315)
	at kotlin.sequences.FlatteningSequence$iterator$1.hasNext(Sequences.kt:303)
	at org.rust.ide.annotator.RsErrorAnnotatorKt.duplicatesByNamespace(RsErrorAnnotator.kt:1324)
	at org.rust.ide.annotator.RsErrorAnnotatorKt.checkDuplicates(RsErrorAnnotator.kt:1124)
	at org.rust.ide.annotator.RsErrorAnnotatorKt.checkDuplicates$default(RsErrorAnnotator.kt:1120)
	at org.rust.ide.annotator.RsErrorAnnotator.checkFunction(RsErrorAnnotator.kt:840)
	at org.rust.ide.annotator.RsErrorAnnotator.access$checkFunction(RsErrorAnnotator.kt:50)
	at org.rust.ide.annotator.RsErrorAnnotator$annotateInternal$visitor$1.visitFunction(RsErrorAnnotator.kt:65)
	at org.rust.lang.core.psi.impl.RsFunctionImpl.accept(RsFunctionImpl.java:27)
	at org.rust.lang.core.psi.impl.RsFunctionImpl.accept(RsFunctionImpl.java:31)
	at org.rust.ide.annotator.RsErrorAnnotator.annotateInternal(RsErrorAnnotator.kt:112)
	at com.intellij.ide.annotator.AnnotatorBase.annotate(AnnotatorBase.kt:21)
	at com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitor.runAnnotators(DefaultHighlightVisitor.java:136)
	at com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitor.visit(DefaultHighlightVisitor.java:116)
	at com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.runVisitors(GeneralHighlightingPass.java:336)
	at com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.lambda$collectHighlights$5(GeneralHighlightingPass.java:269)
	at com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.analyzeByVisitors(GeneralHighlightingPass.java:295)
	at com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.lambda$analyzeByVisitors$6(GeneralHighlightingPass.java:298)
	at com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitor.analyze(DefaultHighlightVisitor.java:96)
	at com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.analyzeByVisitors(GeneralHighlightingPass.java:298)
	at com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.collectHighlights(GeneralHighlightingPass.java:266)
	at com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass.collectInformationWithProgress(GeneralHighlightingPass.java:212)
	at com.intellij.codeInsight.daemon.impl.ProgressableTextEditorHighlightingPass.doCollectInformation(ProgressableTextEditorHighlightingPass.java:84)
	at com.intellij.codeHighlighting.TextEditorHighlightingPass.collectInformation(TextEditorHighlightingPass.java:56)
	at com.intellij.codeInsight.daemon.impl.PassExecutorService$ScheduledPass.lambda$doRun$1(PassExecutorService.java:400)
	at com.intellij.openapi.application.impl.ApplicationImpl.tryRunReadAction(ApplicationImpl.java:1137)
	at com.intellij.codeInsight.daemon.impl.PassExecutorService$ScheduledPass.lambda$doRun$2(PassExecutorService.java:393)
	at com.intellij.openapi.progress.impl.CoreProgressManager.registerIndicatorAndRun(CoreProgressManager.java:658)
	at com.intellij.openapi.progress.impl.CoreProgressManager.executeProcessUnderProgress(CoreProgressManager.java:610)
	at com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:65)
	at com.intellij.codeInsight.daemon.impl.PassExecutorService$ScheduledPass.doRun(PassExecutorService.java:392)
	at com.intellij.codeInsight.daemon.impl.PassExecutorService$ScheduledPass.lambda$run$0(PassExecutorService.java:368)
	at com.intellij.openapi.application.impl.ReadMostlyRWLock.executeByImpatientReader(ReadMostlyRWLock.java:172)
	at com.intellij.openapi.application.impl.ApplicationImpl.executeByImpatientReader(ApplicationImpl.java:183)
	at com.intellij.codeInsight.daemon.impl.PassExecutorService$ScheduledPass.run(PassExecutorService.java:366)
	at com.intellij.concurrency.JobLauncherImpl$VoidForkJoinTask$1.exec(JobLauncherImpl.java:188)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:290)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1020)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1656)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1594)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:183)
* */

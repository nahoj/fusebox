package eu.nahoj.fusebox.archunit;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "eu.nahoj.fusebox")
public class PackageDependencyTest {

    @ArchTest
    static final ArchRule nio_should_not_depend_on_vfs2 = noClasses()
            .that().resideInAnyPackage("eu.nahoj.fusebox.nio..")
            .should().dependOnClassesThat().resideInAnyPackage("eu.nahoj.fusebox.vfs2..");

    @ArchTest
    static final ArchRule vfs2_should_not_depend_on_nio = noClasses()
            .that().resideInAnyPackage("eu.nahoj.fusebox.vfs2..")
            .and().resideOutsideOfPackage("eu.nahoj.fusebox.vfs2.legacy..")
            .should().dependOnClassesThat().resideInAnyPackage("eu.nahoj.fusebox.nio..");
}

package org.wikimedia.utils.generic;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * This annotation can be applied to a package, class or method to indicate that the class fields,
 * method return types and parameters in that element are not null by default unless there is:
 * An explicit nullness annotation
 * The method overrides a method in a superclass (in which case the annotation
 * of the corresponding parameter in the superclass applies) there is a
 * default parameter annotation applied to a more tightly nested element.
 *
 * See <a href="https://stackoverflow.com/a/9256595/14731">this Stackoverflow
 * post</a> for more details.
 */
@Documented
@Nonnull
@TypeQualifierDefault({
        ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.LOCAL_VARIABLE,
        ElementType.METHOD,
        ElementType.PACKAGE,
        ElementType.PARAMETER,
        ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME) public @interface NonNullByDefault {
}

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class SubstituteTypeIntentionTest : RsIntentionTestBase(SubstituteTypeIntention()) {
    fun `test unavailable on trait associated type`() = doUnavailableTest("""
        trait Trait {
            type Item;
        }
        fn foo<T: Trait>() -> T::/*caret*/Item {
            unimplemented!()
        }
    """)

    fun `test trait associated type with a default`() = doAvailableTest("""
        trait Trait {
            type Item = u32;
        }
        fn foo<T: Trait>() -> T::/*caret*/Item {
            unimplemented!()
        }
    """, """
        trait Trait {
            type Item = u32;
        }
        fn foo<T: Trait>() -> u32 {
            unimplemented!()
        }
    """)

    fun `test associated type in type context`() = doAvailableTest("""
        trait Trait {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        impl Trait for () {
            type Item = i32;

            fn foo(&self) -> <Self as Trait>::/*caret*/Item {
                unimplemented!()
            }
        }
    """, """
        trait Trait {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        impl Trait for () {
            type Item = i32;

            fn foo(&self) -> i32 {
                unimplemented!()
            }
        }
    """)

    fun `test associated type in type context with type parameters`() = doAvailableTest("""
        struct S<R>(R);
        trait Trait<T> {
            type Item;
            fn foo(&self, item: Self::Item) -> T;
        }
        impl<T> Trait<T> for () {
            type Item = S<T>;

            fn foo(&self, item: Self::/*caret*/Item) -> T {
                unimplemented!()
            }
        }
    """, """
        struct S<R>(R);
        trait Trait<T> {
            type Item;
            fn foo(&self, item: Self::Item) -> T;
        }
        impl<T> Trait<T> for () {
            type Item = S<T>;

            fn foo(&self, item: S<T>) -> T {
                unimplemented!()
            }
        }
    """)

    fun `test associated type in expression context`() = doAvailableTest("""
        struct S;
        impl S {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S;
        impl S {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S;

            fn foo(&self) {
                S::bar();
            }
        }
    """)

    fun `test associated type in expression context with type parameters`() = doAvailableTest("""
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S<u32>;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = S<u32>;

            fn foo(&self) {
                S::<u32>::bar();
            }
        }
    """)

    fun `test associated type in expression context with type qual`() = doAvailableTest("""
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        impl Trait for S<u32> {
            type Item = Self;
            fn foo(&self) {}
        }

        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = <S<u32> as Trait>::Item;

            fn foo(&self) {
                <Self as Trait>::/*caret*/Item::bar();
            }
        }
    """, """
        struct S<R>(R);
        impl<R> S<R> {
            fn bar() {}
        }
        impl Trait for S<u32> {
            type Item = Self;
            fn foo(&self) {}
        }

        trait Trait {
            type Item;
            fn foo(&self);
        }
        impl Trait for () {
            type Item = <S<u32> as Trait>::Item;

            fn foo(&self) {
                S::<u32>::bar();
            }
        }
    """)

    fun `test substitute type alias`() = doAvailableTest("""
        type T = u32;
        fn foo() -> /*caret*/T { unreachable!() }
    """, """
        type T = u32;
        fn foo() -> u32 { unreachable!() }
    """)

    fun `test substitute generic argument type context`() = doAvailableTest("""
        type A<T> = T;
        fn foo() -> /*caret*/A<u32> { unreachable!() }
    """, """
        type A<T> = T;
        fn foo() -> u32 { unreachable!() }
    """)

    fun `test substitute generic argument expression context`() = doAvailableTest("""
        type A<T> = T;
        fn foo() {
            let _ = A/*caret*/::<u32>::MAX;
        }
    """, """
        type A<T> = T;
        fn foo() {
            let _ = u32::MAX;
        }
    """)

    fun `test substitute recursive type alias`() = doAvailableTest("""
        type A<T> = T;
        type B<T> = A<T>;
        fn foo(b: B/*caret*/<u32>) {}
    """, """
        type A<T> = T;
        type B<T> = A<T>;
        fn foo(b: u32) {}
    """)

    fun `test tuple type in type context`() = doAvailableTest("""
        type A<T> = (T, T);
        fn foo() -> /*caret*/A<u32> { unreachable!() }
    """, """
        type A<T> = (T, T);
        fn foo() -> (u32, u32) { unreachable!() }
    """)

    fun `test tuple type in expression context`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }
        impl Trait for (u32, u32) {
            fn foo(&self) {}
        }

        type A<T> = (T, T);
        fn foo() {
            A/*caret*/::<u32>::foo(&(0, 0));
        }
    """, """
        trait Trait {
            fn foo(&self);
        }
        impl Trait for (u32, u32) {
            fn foo(&self) {}
        }

        type A<T> = (T, T);
        fn foo() {
            <(u32, u32)>::foo(&(0, 0));
        }
    """)

    fun `test nested path in expression context`() = doAvailableTest("""
        trait Trait {
            fn foo(&self);
        }
        impl Trait for (u32, u32) {
            fn foo(&self) {}
        }
        type A<T> = (T, T);

        fn main() {
            crate::A::<u32>::foo(&(0, 0));
        }
    """, """
        trait Trait {
            fn foo(&self);
        }
        impl Trait for (u32, u32) {
            fn foo(&self) {}
        }
        type A<T> = (T, T);

        fn main() {
            <(u32, u32)>::foo(&(0, 0));
        }
    """)

    fun `test import type after substitution`() = doAvailableTest("""
        use foo::B;

        mod foo {
            pub struct A;
            pub type B = A;
        }
        fn foo() -> /*caret*/B { unreachable!() }
    """, """
        use foo::{B, A};

        mod foo {
            pub struct A;
            pub type B = A;
        }
        fn foo() -> A { unreachable!() }
    """)
}

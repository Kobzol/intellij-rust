/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ChopLiteralFieldListIntentionTest : RsIntentionTestBase(ChopLiteralFieldListIntention()) {
    fun `test one parameter`() = doUnavailableTest("""
        struct S { x: i32 }
        fn foo {
            S { /*caret*/x: i32 };
        }
    """)

    fun `test two parameter`() = doAvailableTest("""
        struct S { x: i32, y: i32 }
        fn foo() {
            S { /*caret*/x: i32, y: i32 };
        }
    """, """
        struct S { x: i32, y: i32 }
        fn foo() {
            S {
                x: i32,
                y: i32
            };
        }
    """)

    fun `test has all line breaks`() = doUnavailableTest("""
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S {
                /*caret*/x: i32,
                y: i32,
                z: i32
            };
        }
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S { x: i32, /*caret*/y: i32,
                z: i32
            };
        }
    """, """
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S {
                x: i32,
                y: i32,
                z: i32
            };
        }
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S {
                x: i32, y: i32, z: i32/*caret*/
            };
        }
    """, """
        struct S { x: i32, y: i32, z: i32 }
        fn foo() {
            S {
                x: i32,
                y: i32,
                z: i32
            };
        }
    """)

    fun `test has comment`() = doUnavailableTest("""
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            S { 
                /*caret*/x: i32, /* comment */ 
                y: i32,
                z: i32
            };
        }
    """)

    fun `test has comment 2`() = doAvailableTest("""
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            S { 
                /*caret*/x: i32, /*
                    comment
                */y: i32,
                z: i32
            };
        }
    """, """
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            S {
                x: i32, /*
                    comment
                */
                y: i32,
                z: i32
            };
        }
    """)

    fun `test has single line comment`() = doAvailableTest("""
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            S {
                /*caret*/x: i32, // comment x
                y: i32, z: i32 // comment z
            };
        }
   """, """
        struct S {  x: i32, y: i32, z: i32 }
        fn foo() {
            S {
                x: i32, // comment x
                y: i32,
                z: i32 // comment z
            };
        }
    """)
}

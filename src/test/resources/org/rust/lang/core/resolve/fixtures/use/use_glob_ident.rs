mod foo {
    pub fn hello() {}
}

mod bar {
    use foo::{hello};

    fn main() {
        <caret>hello();
    }
}

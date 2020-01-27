trait Trait {
    type T;
    const C: u32;

    fn test() -> u32;
}

impl Trait for () {
    type T = ();
    const C: u32 = unimplemented!();

    fn test() -> u32 {
        unimplemented!()
    }
}

fn main() {
    println!("Hello, World");
}

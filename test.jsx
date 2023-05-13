export function AppTranspileTest() {
    return <h1>Hello, World!</h1>
}

export const header = content => <header>{content}</header>



console.log(header(AppTranspileTest()))
# Repository settings response contract

Repository-setting mutations replace the matching repository in frontend state.
Budget, intake and run-record saves must therefore select the complete shared
repository field set, not only the changed values. A partial budget response
previously removed `requiredGates` and crashed the repository card's render.

The shared GraphQL selection keeps load/save responses consistent. The budget
regression asserts that the response selection retains all card/settings fields.

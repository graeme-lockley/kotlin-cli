package kli.dispatcher

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Expected <qualified-name> as first argument" }
    val qualifiedName = args[0]
    val mainArgs = args.copyOfRange(1, args.size)
    val klass = Class.forName("${qualifiedName}Kt")
    val method = klass.getMethod("main", Array<String>::class.java)
    method.invoke(null, mainArgs as Any)
}

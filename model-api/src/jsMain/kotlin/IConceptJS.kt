
@JsExport
interface IConceptJS {
    fun getUID(): String
    fun getDirectSuperConcepts(): Array<IConceptJS>
}

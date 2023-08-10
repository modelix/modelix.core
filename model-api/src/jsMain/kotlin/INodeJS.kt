
@JsExport
interface INodeJS {
    fun getConcept(): IConceptJS?
    fun getConceptUID(): String?
    fun getReference(): INodeReferenceJS
    fun getRoleInParent(): String?
    fun getParent(): INodeJS?
    fun getChildren(role: String?): Array<INodeJS>
    fun getAllChildren(): Array<INodeJS>
    fun moveChild(role: String?, index: Int, child: INodeJS)
    fun addNewChild(role: String?, index: Int, concept: IConceptJS?): INodeJS
    fun removeChild(child: INodeJS)
    fun getReferenceRoles(): Array<String>
    fun getReferenceTargetNode(role: String): INodeJS?
    fun getReferenceTargetRef(role: String): INodeReferenceJS?
    fun setReferenceTargetNode(role: String, target: INodeJS?)
    fun setReferenceTargetRef(role: String, target: INodeReferenceJS?)
    fun getPropertyRoles(): Array<String>
    fun getPropertyValue(role: String): String?
    fun setPropertyValue(role: String, value: String?)
}

typealias INodeReferenceJS = Any

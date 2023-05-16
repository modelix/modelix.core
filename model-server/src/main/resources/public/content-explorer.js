async function createInspectorDetails(nodeId) {
    let response = await window.fetch(window.location.pathname + nodeId + '/');
    let nodeInspector = document.getElementById('nodeInspector');
    nodeInspector.innerHTML = await response.text();
    nodeInspector.style.display = 'block';
}
document.addEventListener('DOMContentLoaded', () => {
    var expander = document.getElementsByClassName('expander');
    var nameField = document.getElementsByClassName('nameField');
    var expandAllBtn = document.getElementById('expandAllBtn');
    var collapseAllBtn = document.getElementById('collapseAllBtn');

    for (let i = 0; i < nameField.length; i++) {
        nameField[i].addEventListener('click', function() {
             let isSelected = this.classList.contains('selectedNameField');
             if (isSelected) {
                document.getElementById('nodeInspector').style.display = 'none';
             } else {
                createInspectorDetails(this.dataset.nodeid);
             }
             let selected = document.getElementsByClassName('selectedNameField');
             for (let j = 0; j < selected.length; j++) {
                selected[j].classList.remove('selectedNameField');
             }
             if (!isSelected) {
                this.classList.add('selectedNameField');
             }
        });
    }

    for (let i = 0; i < expander.length; i++) {
        expander[i].addEventListener('click', function() {
            this.parentElement.querySelector(".nested").classList.toggle('active');
            this.classList.toggle('expander-expanded');
        });
    }

    expandAllBtn.addEventListener('click', function () {
        var nested = document.getElementsByClassName("nested")
        for (let i=0; i < nested.length; i++) {
            nested[i].classList.add('active');
        }
        for (let i = 0; i < expander.length; i++) {
            expander[i].classList.add('expander-expanded')
        }
    });

    collapseAllBtn.addEventListener('click', function () {
        var nested = document.getElementsByClassName('nested')
        for (let i=0; i < nested.length; i++) {
            nested[i].classList.remove('active');
        }
        for (let i = 0; i < expander.length; i++) {
            expander[i].classList.remove('expander-expanded')
        }
    });
});
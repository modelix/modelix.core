async function createInspectorDetails(nodeId) {
    let response = await window.fetch(window.location.pathname + nodeId + '/');
    let nodeInspector = document.getElementById('nodeInspector');
    nodeInspector.innerHTML = await response.text();
    nodeInspector.style.display = 'block';
}

function getExpandedNodeIds() {
    const expandedElements = document.getElementsByClassName('expander-expanded');
    return Array.from(expandedElements).map(
        element => element.nextElementSibling?.getAttribute('data-nodeid'));
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars -- Used by HTML generated in Kotlin
function scrollToElement(id) {
    document.getElementById(id).scrollIntoView();
}

function sendExpandNodeRequest(expandAll) {
    const xhr = new XMLHttpRequest();
    xhr.onreadystatechange = () => {
        if (xhr.readyState === 4 && xhr.status === 200) {
            document.getElementById('treeWrapper').innerHTML = xhr.response;
            addContentExplorerClickListeners();
        }
    }
    xhr.open("POST", window.location.href, true);
    xhr.setRequestHeader("Content-Type", "application/json;charset=UTF-8");
    xhr.send(JSON.stringify({"expandedNodeIds": getExpandedNodeIds(), "expandAll" : expandAll}));
}

function addContentExplorerClickListeners() {

    const nameField = document.getElementsByClassName('nameField');
    for (let i = 0; i < nameField.length; i++) {
        nameField[i].addEventListener('click', function () {
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
                this.classList.remove('expandedToNameField');
                this.classList.add('selectedNameField');
            }
        });
    }

    const expander = document.getElementsByClassName('expander');
    for (let i = 0; i < expander.length; i++) {
        expander[i].addEventListener('click', function () {
            this.parentElement.querySelector('.nested').classList.toggle('active');
            this.classList.toggle('expander-expanded');
            localStorage.setItem('scrollY', String(window.scrollY));
            sendExpandNodeRequest(false);
        });
    }
}

document.addEventListener('DOMContentLoaded', () => {

    const scrollY = localStorage.getItem('scrollY');
    if (scrollY) {
        window.scrollTo(0, Number(scrollY));
        localStorage.removeItem('scrollY');
    }

    addContentExplorerClickListeners();

    document.getElementById('expandAllBtn').addEventListener('click', function () {
        sendExpandNodeRequest(true);
    });

    document.getElementById('collapseAllBtn').addEventListener('click', function () {
        window.location.href = window.location.pathname
    });
});

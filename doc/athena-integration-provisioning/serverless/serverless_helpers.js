var username = process.env['OVERRIDE_USER'];
module.exports.userName = function() {
    if (username) {
        return username
    }
    var _mUsername = runCommand("aws sts get-caller-identity --output text --query 'UserId'");
    if (!_mUsername || !validUserID(_mUsername)) {
        // if did not receive username from aws command, get username from system environment
        _mUsername = process.env['USER'];
    }

    // remove dot from the username, trim white-space and convert to lowercase (required for some AWS resources)
    username = _mUsername.split(":").pop().replace(/\./g,'').trim().toLowerCase();
    if (username.length > 15) {
        username = username.substring(0, 15);
    }

    return username
};

function runCommand(cmd) {
    var response = null
    try {
        var child_process = require('child_process');
        response = child_process.execSync(cmd, { encoding: 'utf8' })
    } catch (err) {
        console.log("Warning. Error executing: <" + cmd + ">: " + err.message)
    }
    return response
}

function validUserID(userId) {
    var simplified = userId.split(":").pop().replace(/\./g,'').trim().toLowerCase();
    return !simplified.startsWith("awscodebuild") && !simplified.startsWith("awscodepipeline")
}

/**
 * Gets milliseconds between midnight January 1, 1970 and current time.
 * @returns {number} timestamp
 */
module.exports.timestamp = function () {
    return new Date().getTime()
};

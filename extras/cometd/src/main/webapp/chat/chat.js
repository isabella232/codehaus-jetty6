

var EvUtil =
{
    getKeyCode : function(ev)
    {
        var keyc;
        if (window.event)
            keyc=window.event.keyCode;
        else
            keyc=ev.keyCode;
        return keyc;
    }
};

var room = 
{
  _last: "",
  _username: null,
  
  join: function(name)
  {
    if (name == null || name.length==0 )
    {
      alert('Please enter a username!');
    }
    else
    {
       this._username=name;
       $('join').className='hidden';
       $('joined').className='';
       $('phrase').focus();
       Behaviour.apply();
       
	   cometd.subscribe("/chat/demo", false, room, "_chat");
	   cometd.publish("/chat/demo", { user: room._username, join: true, chat : room._username+" has joined"});
	   
       // XXX ajax.sendMessage('join', room._username);
    }
  },
  
  leave: function()
  {
	   cometd.unsubscribe("/chat/demo", false, room, "_chat");
	   cometd.publish("/chat/demo", { user: room._username, leave: true, chat : room._username+" has left"});
	   
       // switch the input form
       $('join').className='';
       $('joined').className='hidden';
       $('username').focus();
       Behaviour.apply();
       // XXX ajax.sendMessage('leave',room._username);
       room._username=null;
  },
  
  chat: function(text)
  {
    if (text != null && text.length>0 )
    {
        // XXX ajax.sendMessage('chat',text);
	    cometd.publish("/chat/demo", { user: room._username, chat: text});
    }
  },
  
  _chat: function(message)
  {
     var chat=$('chat');
     var from=message.data.user;
     var special=message.data.join || message.data.leave;
     var text=message.data.chat;
     if ( !special && from == room._last )
         from="...";
     else
     {
         room._last=from;
         from+=":";
     }
     
     if (special)
     {
       chat.innerHTML += "<span class=\"alert\"><span class=\"from\">"+from+"&nbsp;</span><span class=\"text\">"+text+"</span></span><br/>";
       room._last="";
     }
     else
       chat.innerHTML += "<span class=\"from\">"+from+"&nbsp;</span><span class=\"text\">"+text+"</span><br/>";
     chat.scrollTop = chat.scrollHeight - chat.clientHeight;     
  },
  
  _init: function()
  {
	   cometd.init({}, "/cometd");
				
	   
       // XXX ajax.addListener('chat',room._chat);
       // XXX ajax.addListener('joined',room._joined);
       // XXX ajax.addListener('left',room._left);
       // XXX ajax.addListener('members',room._members);
       $('join').className='';
       $('joined').className='hidden';
       $('username').focus();
      Behaviour.apply();
  }
};

Behaviour.addLoadEvent(room._init);  

var chatBehaviours = 
{ 
  '#username' : function(element)
  {
    element.setAttribute("autocomplete","OFF"); 
    element.onkeyup = function(ev)
    {          
        var keyc=EvUtil.getKeyCode(ev);
        if (keyc==13 || keyc==10)
        {
          room.join($F('username'));
	  return false;
	}
	return true;
    } 
  },
  
  '#joinB' : function(element)
  {
    element.onclick = function(event)
    {
      room.join($F('username'));
      return false;
    }
  },
  
  '#phrase' : function(element)
  {
    element.setAttribute("autocomplete","OFF");
    element.onkeyup = function(ev)
    {   
        var keyc=EvUtil.getKeyCode(ev);
        if (keyc==13 || keyc==10)
        {
          room.chat($F('phrase'));
          $('phrase').value='';
	  return false;
	}
	return true;
    }
  },
  
  '#sendB' : function(element)
  {
    element.onclick = function(event)
    {
      room.chat($F('phrase'));
      $('phrase').value='';
      return false;
    }
  },
  
  
  '#leaveB' : function(element)
  {
    element.onclick = function()
    {
      room.leave();
      return false;
    }
  }
};

Behaviour.register(chatBehaviours); 


